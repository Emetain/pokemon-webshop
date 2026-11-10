package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.DropQueueEntry
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface DropQueueEntryRepository : JpaRepository<DropQueueEntry, Long> {

    fun findByDropIdAndUserId(dropId: Long, userId: Long): DropQueueEntry?

    fun findByDropIdAndVisitorTokenHash(dropId: Long, visitorTokenHash: String): DropQueueEntry?

    fun findByAccessTokenHash(accessTokenHash: String): DropQueueEntry?

    /**
     * Everyone in the waiting room without a place: they get a random one at the start. A join after the start time
     * starts the drop first, under the same lock, so only people who joined before the start have no place.
     */
    @Query("select e.id from DropQueueEntry e where e.drop.id = :dropId and e.position is null order by e.id")
    fun findIdsWithoutPosition(dropId: Long): List<Long>

    /** The next people in line who are still there. The batch ends at the last of them. */
    @Query(
        "select e.position from DropQueueEntry e where e.drop.id = :dropId and e.position > :after " +
            "and e.lastSeenAt >= :seenSince order by e.position",
    )
    fun findActivePositionsAfter(dropId: Long, after: Long, seenSince: Instant, page: Pageable): List<Long>

    /** People still waiting between the last admitted place and [before], who are still there. */
    @Query(
        "select count(e) from DropQueueEntry e where e.drop.id = :dropId and e.position > :after and e.position < :before " +
            "and e.lastSeenAt >= :seenSince",
    )
    fun countActiveBetween(dropId: Long, after: Long, before: Long, seenSince: Instant): Long

    fun countByDropId(dropId: Long): Long

    @Query("select count(e) from DropQueueEntry e where e.drop.id = :dropId and (e.position is null or e.position > :after) and e.lastSeenAt >= :seenSince")
    fun countActiveWaiting(dropId: Long, after: Long, seenSince: Instant): Long

    @Query("select count(e) from DropQueueEntry e where e.drop.id = :dropId and (e.position is null or e.position > :after)")
    fun countWaiting(dropId: Long, after: Long): Long

    fun countByDropIdAndAdmittedAtIsNotNull(dropId: Long): Long

    fun countByDropIdAndAccessExpiresAtAfter(dropId: Long, now: Instant): Long
}
