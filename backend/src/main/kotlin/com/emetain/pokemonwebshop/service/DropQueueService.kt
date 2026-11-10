package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Drop
import com.emetain.pokemonwebshop.domain.DropQueueEntry
import com.emetain.pokemonwebshop.domain.DropStatus
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.DropQueueEntryRepository
import com.emetain.pokemonwebshop.repository.DropRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Who is asking: a customer, or a guest with the `drop_visitor` cookie. */
data class DropVisitor(val user: User?, val visitorToken: String?)

/** An entry after a call, with the tokens to put in new cookies. */
data class QueueResult(
    val drop: Drop,
    val entry: DropQueueEntry?,
    val newVisitorToken: String? = null,
    val newAccessToken: String? = null,
)

/**
 * The waiting room of a drop. Whoever joins before the start waits without a place. At the start everyone waiting
 * gets a random place, so refreshing at exactly the right second gives no advantage, and later arrivals join at the
 * back. The scheduler lets people through in batches, at the speed the admin set. Someone who got through gets an
 * access token for a limited time to fill the drop cart and start a checkout.
 */
@Service
class DropQueueService(
    private val dropRepository: DropRepository,
    private val dropLocks: DropLocks,
    private val entryRepository: DropQueueEntryRepository,
    private val tokenService: TokenService,
    private val properties: AppProperties,
    private val entityManager: EntityManager,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Joins the waiting room, or gets back in line at the back after the time ran out. Joining again while waiting
     * or shopping changes nothing. Runs under the lock on the drop, so nobody joins in the middle of the shuffle.
     */
    @Transactional
    fun join(dropId: Long, visitor: DropVisitor): QueueResult {
        val drop = dropLocks.lock(dropId) ?: throw notFound()
        val now = clock.instant()
        startIfDue(drop, now)
        when (drop.status) {
            DropStatus.DRAFT -> throw notFound()
            DropStatus.ENDED -> throw ended()
            DropStatus.SCHEDULED -> if (now.isBefore(DropRules.waitingRoomOpensAt(drop.startsAt, properties.drops.waitingRoomOpens))) {
                throw ApiException(HttpStatus.CONFLICT, "waiting_room_closed", "De wachtkamer is nog niet open. Kom terug vlak voor de start.")
            }
            DropStatus.LIVE -> Unit
        }
        if (drop.requiresAccount && visitor.user == null) throw ApiException.notAuthenticated()

        val existing = find(drop, visitor)
        if (existing != null) {
            existing.lastSeenAt = now
            if (existing.accessExpiresAt != null && !existing.hasAccess(now) && drop.status == DropStatus.LIVE) {
                // The time ran out: back in line, at the back. The drop cart stays, it holds no stock.
                existing.position = drop.nextPosition++
                existing.joinedAt = now
                existing.admittedAt = null
                existing.accessTokenHash = null
                existing.accessExpiresAt = null
                existing.joinCount++
            }
            return QueueResult(drop, existing)
        }

        val newVisitorToken = if (visitor.user == null && visitor.visitorToken == null) tokenService.generate() else null
        val visitorToken = visitor.visitorToken ?: newVisitorToken
        val entry = DropQueueEntry(
            drop = drop,
            user = visitor.user,
            visitorTokenHash = if (visitor.user == null) tokenService.hash(visitorToken!!) else null,
            joinedAt = now,
            lastSeenAt = now,
            position = if (drop.status == DropStatus.LIVE) drop.nextPosition++ else null,
        )
        return QueueResult(drop, entryRepository.save(entry), newVisitorToken = newVisitorToken)
    }

    /**
     * Someone on the drop page asks where they are. Keeps them counted as present, and hands out the access token
     * once their place is let through. A lost cookie gets a new token: the account or visitor cookie proves who it is.
     */
    @Transactional
    fun ping(dropId: Long, visitor: DropVisitor, accessToken: String?): QueueResult {
        val drop = dropRepository.findById(dropId).orElseThrow { notFound() }
        val entry = find(drop, visitor) ?: return QueueResult(drop, null)
        val now = clock.instant()
        val result = issueAccessIfThrough(drop, entry, accessToken, now)
        if (Duration.between(entry.lastSeenAt, now) >= SEEN_UPDATE_EVERY) entry.lastSeenAt = now
        return result
    }

    private fun issueAccessIfThrough(drop: Drop, entry: DropQueueEntry, accessToken: String?, now: Instant): QueueResult {
        val position = entry.position
        if (drop.status != DropStatus.LIVE || position == null || position > drop.admittedUpTo) return QueueResult(drop, entry)
        if (entry.accessExpiresAt != null && !entry.hasAccess(now)) return QueueResult(drop, entry)
        val holdsToken = accessToken != null && tokenService.hash(accessToken) == entry.accessTokenHash
        if (entry.hasAccess(now) && holdsToken) return QueueResult(drop, entry)

        // Through for the first time, or the cookie got lost. One call at a time hands out a token, with fresh numbers.
        entityManager.refresh(entry, LockModeType.PESSIMISTIC_WRITE)
        if (entry.accessExpiresAt != null && !entry.hasAccess(now)) return QueueResult(drop, entry)
        val token = tokenService.generate()
        if (entry.admittedAt == null) {
            entry.admittedAt = now
            entry.accessExpiresAt = now.plus(properties.drops.accessTtl)
        }
        entry.accessTokenHash = tokenService.hash(token)
        return QueueResult(drop, entry, newAccessToken = token)
    }

    /** The entry behind an access token, for the drop cart and the checkout. Fails when it isn't this person's turn. */
    @Transactional(readOnly = true)
    fun requireAccess(drop: Drop, visitor: DropVisitor, accessToken: String?): DropQueueEntry {
        if (drop.requiresAccount && visitor.user == null) throw ApiException.notAuthenticated()
        when (drop.status) {
            DropStatus.LIVE -> Unit
            DropStatus.ENDED -> throw ended()
            else -> throw notYourTurn()
        }
        val entry = accessToken?.let { entryRepository.findByAccessTokenHash(tokenService.hash(it)) } ?: throw notYourTurn()
        val sameDrop = entry.drop.id == drop.id
        val samePerson = if (visitor.user != null) {
            entry.user?.id == visitor.user.id
        } else {
            visitor.visitorToken != null && entry.visitorTokenHash == tokenService.hash(visitor.visitorToken)
        }
        if (!sameDrop || !samePerson) throw notYourTurn()
        if (!entry.hasAccess(clock.instant())) throw accessExpired()
        return entry
    }

    @Transactional(readOnly = true)
    fun find(drop: Drop, visitor: DropVisitor): DropQueueEntry? = when {
        visitor.user != null -> entryRepository.findByDropIdAndUserId(drop.id, visitor.user.id)
        visitor.visitorToken != null -> entryRepository.findByDropIdAndVisitorTokenHash(drop.id, tokenService.hash(visitor.visitorToken))
        else -> null
    }

    /** For the scheduler: starts a drop whose start time came. */
    @Transactional
    fun start(dropId: Long) {
        val drop = dropLocks.lock(dropId) ?: return
        startIfDue(drop, clock.instant())
    }

    /**
     * Call this with the drop locked. At the start time the drop goes live and everyone who joined before the start
     * gets a random place. The first batch goes through right away.
     */
    fun startIfDue(drop: Drop, now: Instant) {
        if (drop.status != DropStatus.SCHEDULED || drop.startsAt.isAfter(now)) return
        drop.status = DropStatus.LIVE
        drop.startedAt = now
        drop.updatedAt = now

        val waiting = entryRepository.findAllById(entryRepository.findIdsWithoutPosition(drop.id))
        DropRules.shuffle(waiting).forEachIndexed { index, entry -> entry.position = (index + 1).toLong() }
        drop.shuffledAt = now
        drop.shuffledCount = waiting.size
        drop.nextPosition = waiting.size + 1L
        drop.admittedUpTo = 0
        // As if one batch interval has passed, so the first batch goes through now instead of after the interval.
        drop.admittedUntil = now.minus(properties.drops.admitInterval)
        log.info("Drop {} is live with {} in the waiting room", drop.slug, waiting.size)
        admit(drop, now)
    }

    /**
     * Call this with the drop locked. Lets the next batch through, once per batch interval, at the speed of the drop.
     * Only people who are still on the page count towards the batch: places of people who left are passed over.
     * Returns how many got through.
     */
    fun admit(drop: Drop, now: Instant): Int {
        if (drop.status != DropStatus.LIVE || drop.queuePaused) return 0
        val interval = properties.drops.admitInterval
        val last = drop.admittedUntil
        if (last != null && Duration.between(last, now) < interval) return 0
        val batch = DropRules.admissionBatch(drop.admitPerMinute, last, now, interval)
        drop.admittedUntil = batch.usedUntil
        if (batch.size == 0) return 0
        val seenSince = now.minus(properties.drops.activeWindow)
        val positions = entryRepository.findActivePositionsAfter(drop.id, drop.admittedUpTo, seenSince, PageRequest.of(0, batch.size))
        if (positions.isEmpty()) return 0
        drop.admittedUpTo = positions.last()
        return positions.size
    }

    /** People who are still there and wait before [entry]. */
    @Transactional(readOnly = true)
    fun ahead(drop: Drop, entry: DropQueueEntry): Long {
        val position = entry.position ?: return 0
        if (position <= drop.admittedUpTo) return 0
        val seenSince = clock.instant().minus(properties.drops.activeWindow)
        return entryRepository.countActiveBetween(drop.id, drop.admittedUpTo, position, seenSince)
    }

    private fun notFound() = ApiException.notFound("We konden deze drop niet vinden.")

    companion object {
        /** A ping saves the time it came at most this often, to keep the waiting room from writing on every call. */
        val SEEN_UPDATE_EVERY: Duration = Duration.ofSeconds(15)

        fun ended() = ApiException(HttpStatus.CONFLICT, "drop_ended", "Deze drop is afgelopen.")

        fun notYourTurn() = ApiException(HttpStatus.FORBIDDEN, "drop_no_access", "Je bent nog niet aan de beurt. Wacht in de rij tot je erin mag.")

        fun accessExpired() = ApiException(HttpStatus.FORBIDDEN, "drop_access_expired", "Je tijd om te bestellen is om. Ga opnieuw in de rij.")
    }
}
