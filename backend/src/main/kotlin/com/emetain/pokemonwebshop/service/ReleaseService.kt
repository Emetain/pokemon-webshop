package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.HeroSlide
import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.SetRelease
import com.emetain.pokemonwebshop.dto.HeroSlideRequest
import com.emetain.pokemonwebshop.dto.SetReleaseRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.repository.SetReleaseRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The release calendar and the pre-order timeline. */
@Service
class ReleaseService(
    private val releaseRepository: SetReleaseRepository,
    private val productRepository: ProductRepository,
    private val cardSetRepository: CardSetRepository,
    private val heroSlideService: HeroSlideService,
    private val mediaStorage: MediaStorage,
    private val clock: Clock,
) {

    /** Today and later, plus what came out in the last [recentDays] days, so a new set doesn't vanish on its day. */
    @Transactional(readOnly = true)
    fun calendar(game: Game?, recentDays: Long = RECENT_DAYS): List<SetRelease> =
        releaseRepository.findPublishedFrom(today().minusDays(recentDays), game)

    /** The pre-orders of a release: those the admin picked, or else the pre-orders of its set. */
    @Transactional(readOnly = true)
    fun preOrdersOf(release: SetRelease): List<Product> {
        val picked = release.products.filter { it.status == ProductStatus.ACTIVE }
        if (picked.isNotEmpty()) return picked
        return release.cardSet?.let { productRepository.findPreOrdersOfSet(it.id) }.orEmpty().take(MAX_PRODUCTS)
    }

    @Transactional(readOnly = true)
    fun preOrders(game: Game?): List<Product> = productRepository.findPreOrders(game, PageRequest.of(0, MAX_PRE_ORDERS))

    @Transactional(readOnly = true)
    fun all(): List<SetRelease> = releaseRepository.findAllForAdmin()

    @Transactional(readOnly = true)
    fun find(releaseId: Long): SetRelease =
        releaseRepository.findById(releaseId).orElseThrow { ApiException.notFound("We konden deze release niet vinden.") }

    @Transactional
    fun create(request: SetReleaseRequest): SetRelease {
        val now = clock.instant()
        val release = SetRelease(game = Game.POKEMON, name = "", releaseDate = today(), createdAt = now, updatedAt = now)
        apply(release, request)
        return releaseRepository.save(release)
    }

    @Transactional
    fun update(releaseId: Long, request: SetReleaseRequest): SetRelease = find(releaseId).also {
        apply(it, request)
        it.updatedAt = clock.instant()
    }

    @Transactional
    fun delete(releaseId: Long) {
        val release = find(releaseId)
        release.imageUrl?.let(mediaStorage::delete)
        releaseRepository.delete(release)
    }

    @Transactional
    fun replaceImage(releaseId: Long, bytes: ByteArray): SetRelease {
        val release = find(releaseId)
        val format = ImageFormat.detect(bytes) ?: throw ApiException.validation("Kies een afbeelding in JPG, PNG of WebP.")
        val previous = release.imageUrl
        release.imageUrl = mediaStorage.store(bytes, format)
        previous?.let(mediaStorage::delete)
        return release
    }

    @Transactional
    fun removeImage(releaseId: Long): SetRelease {
        val release = find(releaseId)
        release.imageUrl?.let(mediaStorage::delete)
        release.imageUrl = null
        return release
    }

    /**
     * Makes a slide for the home page hero in one click: the name, the date and a button to the release on the
     * calendar, in the colour of its game, until the day after the release. The admin edits it at /admin/hero.
     * The picture is copied, so removing the slide never removes the picture of the release.
     */
    @Transactional
    fun createSlide(releaseId: Long): HeroSlide {
        val release = find(releaseId)
        val zone = ZoneId.of(ZONE)
        val slide = heroSlideService.create(
            HeroSlideRequest(
                title = release.name.take(TITLE_LENGTH),
                text = slideText(release),
                buttonLabel = if (preOrdersOf(release).isNotEmpty()) "Bekijk de pre-orders" else "Bekijk de release",
                linkUrl = "/releasekalender#release-${release.id}",
                game = release.game.slug,
                active = true,
                endsAt = release.releaseDate.plusDays(SLIDE_DAYS_AFTER).atStartOfDay(zone).toInstant(),
            ),
        )
        release.imageUrl?.let(::copyOf)?.let { heroSlideService.replaceImage(slide.id, it) }
        return slide
    }

    private fun slideText(release: SetRelease): String {
        val date = if (release.tentative) {
            "Verwacht in ${release.releaseDate.format(MONTH)}"
        } else {
            "Verschijnt op ${release.releaseDate.format(DAY)}"
        }
        return "$date. Bestel nu je pre-order, dan heb je hem op tijd binnen."
    }

    private fun copyOf(url: String): ByteArray? =
        mediaStorage.read(url.substringAfterLast('/'))?.resource?.inputStream?.use { it.readBytes() }

    private fun apply(release: SetRelease, request: SetReleaseRequest) {
        val name = request.name.trim()
        if (name.isEmpty()) throw ApiException.validation("Vul de naam van de set in.")
        val game = Game.fromSlug(request.game) ?: throw ApiException.validation("Kies een geldig spel.")
        val date = request.releaseDate ?: throw ApiException.validation("Vul de releasedatum in.")
        val set = request.setCode?.trim()?.takeIf { it.isNotEmpty() }?.let {
            cardSetRepository.findByGameAndCode(game, it) ?: throw ApiException.validation("Deze set staat niet in de kaartencatalogus van dit spel.")
        }
        val products = if (request.productIds.isEmpty()) {
            emptyList()
        } else {
            val found = productRepository.findAllByIdIn(request.productIds).associateBy { it.id }
            request.productIds.distinct().map { found[it] ?: throw ApiException.validation("Een gekozen product bestaat niet meer.") }
        }
        if (products.size > MAX_PRODUCTS) throw ApiException.validation("Kies maximaal $MAX_PRODUCTS producten.")
        release.game = game
        release.name = name
        release.releaseDate = date
        release.tentative = request.tentative
        release.description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        release.cardSet = set
        release.published = request.published
        release.products.clear()
        release.products.addAll(products)
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneId.of(ZONE)))

    private companion object {
        const val ZONE = "Europe/Amsterdam"
        const val RECENT_DAYS = 14L
        const val MAX_PRODUCTS = 12
        const val MAX_PRE_ORDERS = 200
        const val TITLE_LENGTH = 120
        const val SLIDE_DAYS_AFTER = 1L
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("nl-NL"))
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("nl-NL"))
    }
}
