package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Drop
import com.emetain.pokemonwebshop.domain.DropProduct
import com.emetain.pokemonwebshop.domain.DropStatus
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.DropDetailDto
import com.emetain.pokemonwebshop.dto.DropListDto
import com.emetain.pokemonwebshop.dto.DropProductDto
import com.emetain.pokemonwebshop.dto.DropReminderRequest
import com.emetain.pokemonwebshop.dto.DropSummaryDto
import com.emetain.pokemonwebshop.dto.HoneypotRequest
import com.emetain.pokemonwebshop.dto.JoinQueueRequest
import com.emetain.pokemonwebshop.dto.QueueStatusDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.DropQueueEntryRepository
import com.emetain.pokemonwebshop.repository.DropRepository
import com.emetain.pokemonwebshop.service.DropCookieService
import com.emetain.pokemonwebshop.service.DropQueueService
import com.emetain.pokemonwebshop.service.DropReminderService
import com.emetain.pokemonwebshop.service.DropRules
import com.emetain.pokemonwebshop.service.DropVisitor
import com.emetain.pokemonwebshop.service.ProductRules
import com.emetain.pokemonwebshop.service.QueueResult
import com.emetain.pokemonwebshop.service.RateLimitService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/** Throws when a bot filled in the field people never see. The message doesn't say why. */
internal fun checkHoneypot(request: HoneypotRequest, what: String) {
    if (!request.website.isNullOrBlank()) {
        LoggerFactory.getLogger("com.emetain.pokemonwebshop.Honeypot").warn("Honeypot filled in on {}", what)
        throw ApiException.validation("We konden dit niet verwerken. Probeer het opnieuw.")
    }
}

/** The public side of drops: the list, the drop page, reminders and the waiting room. */
@Component
class DropOrchestrator(
    private val dropRepository: DropRepository,
    private val entryRepository: DropQueueEntryRepository,
    private val dropQueueService: DropQueueService,
    private val dropReminderService: DropReminderService,
    private val cookies: DropCookieService,
    private val rateLimitService: RateLimitService,
    private val properties: AppProperties,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun list(): DropListDto {
        val drops = dropRepository.findAllWithProducts(listOf(DropStatus.SCHEDULED, DropStatus.LIVE, DropStatus.ENDED))
        return DropListDto(
            live = drops.filter { it.status == DropStatus.LIVE }.sortedBy { it.startsAt }.map(::summary),
            upcoming = drops.filter { it.status == DropStatus.SCHEDULED }.sortedBy { it.startsAt }.map(::summary),
            past = drops.filter { it.status == DropStatus.ENDED }
                .sortedByDescending { it.endedAt ?: it.startsAt }
                .take(PAST_DROPS)
                .map(::summary),
            serverTime = clock.instant(),
        )
    }

    /** For the LIVE badge in the menu. */
    @Transactional(readOnly = true)
    fun live(): List<DropSummaryDto> = dropRepository.findWithStatus(DropStatus.LIVE).map(::summary)

    /** A draft only for an admin, as a preview. */
    @Transactional(readOnly = true)
    fun detail(slug: String, viewer: User?): DropDetailDto {
        val drop = dropRepository.findBySlug(slug)?.takeIf { it.status.public || viewer?.role == Role.ADMIN }
            ?: throw notFound()
        return DropDetailDto(
            slug = drop.slug,
            name = drop.name,
            description = drop.description,
            bannerUrl = drop.bannerUrl,
            startsAt = drop.startsAt,
            endsAt = drop.endsAt,
            endedAt = drop.endedAt,
            status = drop.status.name,
            requiresAccount = drop.requiresAccount,
            maxPerCustomer = drop.maxPerCustomer,
            waitingRoomOpensAt = DropRules.waitingRoomOpensAt(drop.startsAt, properties.drops.waitingRoomOpens),
            accessMinutes = properties.drops.accessTtl.toMinutes(),
            reminderMinutes = properties.drops.reminderBefore.toMinutes(),
            queuePaused = drop.queuePaused,
            soldOut = drop.status != DropStatus.DRAFT && drop.products.isNotEmpty() && drop.products.all { it.soldOut },
            game = gameOf(drop),
            products = drop.products.map(::product),
            serverTime = clock.instant(),
        )
    }

    fun remind(slug: String, viewer: User?, request: DropReminderRequest) {
        checkHoneypot(request, "drop reminder")
        dropReminderService.signUp(publicDrop(slug).id, viewer, request.email)
    }

    @Transactional
    fun join(slug: String, viewer: User?, httpRequest: HttpServletRequest, request: JoinQueueRequest): ResponseEntity<QueueStatusDto> {
        checkHoneypot(request, "drop queue")
        viewer?.let { rateLimitService.checkAccount("drop-join-account", it.id) }
        val visitor = visitor(viewer, httpRequest)
        return respond(dropQueueService.join(publicDrop(slug).id, visitor), visitor, httpRequest)
    }

    @Transactional
    fun ping(slug: String, viewer: User?, httpRequest: HttpServletRequest): ResponseEntity<QueueStatusDto> {
        viewer?.let { rateLimitService.checkAccount("drop-ping-account", it.id) }
        val visitor = visitor(viewer, httpRequest)
        val result = dropQueueService.ping(publicDrop(slug).id, visitor, cookies.accessToken(httpRequest))
        return respond(result, visitor, httpRequest)
    }

    fun visitor(viewer: User?, request: HttpServletRequest) =
        DropVisitor(viewer, if (viewer == null) cookies.visitorToken(request) else null)

    fun publicDrop(slug: String): Drop = dropRepository.findBySlug(slug)?.takeIf { it.status.public } ?: throw notFound()

    private fun respond(result: QueueResult, visitor: DropVisitor, httpRequest: HttpServletRequest): ResponseEntity<QueueStatusDto> {
        val response = ResponseEntity.ok()
        result.newVisitorToken?.let { response.header(HttpHeaders.SET_COOKIE, cookies.visitorCookie(it, httpRequest).toString()) }
        result.newAccessToken?.let { token ->
            val validFor = Duration.between(clock.instant(), result.entry!!.accessExpiresAt)
            response.header(HttpHeaders.SET_COOKIE, cookies.accessCookie(token, result.drop.slug, validFor, httpRequest).toString())
        }
        return response.body(status(result, visitor))
    }

    private fun status(result: QueueResult, visitor: DropVisitor): QueueStatusDto {
        val drop = result.drop
        val entry = result.entry
        val now = clock.instant()
        val opensAt = DropRules.waitingRoomOpensAt(drop.startsAt, properties.drops.waitingRoomOpens)
        val state = when {
            drop.status == DropStatus.ENDED || drop.status == DropStatus.DRAFT -> QueueState.CLOSED
            entry == null && drop.requiresAccount && visitor.user == null -> QueueState.LOGIN_REQUIRED
            entry == null && drop.status == DropStatus.SCHEDULED && now.isBefore(opensAt) -> QueueState.NOT_OPEN
            entry == null -> QueueState.NOT_JOINED
            entry.hasAccess(now) -> QueueState.ADMITTED
            entry.accessExpiresAt != null -> QueueState.EXPIRED
            entry.position == null || drop.status == DropStatus.SCHEDULED -> QueueState.WAITING_ROOM
            else -> QueueState.QUEUED
        }
        val interval = properties.drops.admitInterval
        val ahead = if (state == QueueState.QUEUED) dropQueueService.ahead(drop, entry!!) else null
        val seenSince = now.minus(properties.drops.activeWindow)
        val waiting = when (state) {
            QueueState.WAITING_ROOM, QueueState.NOT_JOINED -> if (drop.status == DropStatus.SCHEDULED) {
                entryRepository.countByDropId(drop.id)
            } else {
                entryRepository.countActiveWaiting(drop.id, drop.admittedUpTo, seenSince)
            }
            QueueState.QUEUED -> entryRepository.countActiveWaiting(drop.id, drop.admittedUpTo, seenSince)
            else -> null
        }
        val estimated = ahead?.let { DropRules.estimatedWaitSeconds(it, drop.admitPerMinute, interval) }
        return QueueStatusDto(
            state = state.name,
            position = ahead?.plus(1),
            ahead = ahead,
            estimatedSeconds = estimated,
            waitingCount = waiting,
            accessExpiresAt = entry?.accessExpiresAt?.takeIf { state == QueueState.ADMITTED },
            cartCount = if (state == QueueState.ADMITTED) entry!!.items.sumOf { it.quantity } else 0,
            paused = drop.queuePaused,
            serverTime = now,
            pollAfterMs = pollAfter(state, drop, estimated, now, opensAt),
        )
    }

    /**
     * When the page should ask again. Close to the start, or close to the front of the line, it asks sooner. A little
     * randomness spreads a big waiting room over a few seconds instead of everyone asking at the same moment.
     */
    private fun pollAfter(state: QueueState, drop: Drop, estimatedSeconds: Long?, now: Instant, opensAt: Instant): Long {
        val jitter = Random.nextLong(JITTER_MS)
        val base = when (state) {
            QueueState.CLOSED -> return 0
            QueueState.LOGIN_REQUIRED, QueueState.NOT_JOINED -> 30_000L
            QueueState.NOT_OPEN -> Duration.between(now, opensAt).toMillis().coerceIn(1_000L, 60_000L)
            QueueState.WAITING_ROOM -> Duration.between(now, drop.startsAt).toMillis().coerceIn(1_000L, 10_000L)
            QueueState.QUEUED -> if ((estimatedSeconds ?: 0) <= properties.drops.admitInterval.toSeconds() * 2) 2_500L else 6_000L
            QueueState.ADMITTED, QueueState.EXPIRED -> 15_000L
        }
        return base + jitter
    }

    private fun summary(drop: Drop) = DropSummaryDto(
        slug = drop.slug,
        name = drop.name,
        bannerUrl = drop.bannerUrl,
        startsAt = drop.startsAt,
        endsAt = drop.endsAt,
        endedAt = drop.endedAt,
        status = drop.status.name,
        productCount = drop.products.size,
        soldOut = drop.products.isNotEmpty() && drop.products.all { it.soldOut },
        game = gameOf(drop),
    )

    private fun product(dropProduct: DropProduct): DropProductDto {
        val product = dropProduct.product
        val shopPrice = ProductRules.shownPriceCents(product)
        val price = dropProduct.priceCents ?: shopPrice
        val ownImage = product.images.firstOrNull()
        val cardImage = product.card?.shownImageSmallUrl
        return DropProductDto(
            id = dropProduct.id,
            productId = product.id,
            slug = product.slug.takeIf { product.status == ProductStatus.ACTIVE },
            name = product.name,
            game = product.game?.slug,
            type = product.type.name,
            imageUrl = ownImage ?: cardImage,
            imageIsCard = ownImage == null && cardImage != null,
            setLogoUrl = product.cardSet?.shownLogoUrl,
            priceCents = price,
            regularPriceCents = shopPrice?.takeIf { price != null && it > price },
            maxPerCustomer = dropProduct.maxPerCustomer,
            allocated = dropProduct.allocated,
            available = dropProduct.available.coerceAtLeast(0),
            reserved = dropProduct.reserved,
            soldOut = dropProduct.soldOut,
            preOrder = product.preOrder,
            shipsFrom = if (product.preOrder) product.shipsFrom ?: product.releaseDate else null,
        )
    }

    private fun notFound() = ApiException.notFound("We konden deze drop niet vinden.")

    private enum class QueueState { LOGIN_REQUIRED, NOT_OPEN, NOT_JOINED, WAITING_ROOM, QUEUED, ADMITTED, EXPIRED, CLOSED }

    companion object {
        private const val PAST_DROPS = 12
        private const val JITTER_MS = 1_500L

        /** The game of every product in the drop, for its colour. Null for a mix or accessories. */
        fun gameOf(drop: Drop): String? = drop.products.mapNotNull { it.product.game }.distinct().singleOrNull()
            ?.takeIf { game -> drop.products.all { it.product.game == game } }?.slug
    }
}
