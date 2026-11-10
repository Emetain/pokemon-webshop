package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Drop
import com.emetain.pokemonwebshop.domain.DropStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface DropRepository : JpaRepository<Drop, Long> {

    fun findBySlug(slug: String): Drop?

    fun existsBySlug(slug: String): Boolean

    @EntityGraph(attributePaths = ["products", "products.product"])
    @Query("select d from Drop d where d.status in :statuses order by d.startsAt desc, d.id desc")
    fun findAllWithProducts(statuses: Collection<DropStatus>): List<Drop>

    @Query("select d from Drop d order by d.startsAt desc, d.id desc")
    fun findAllNewestFirst(): List<Drop>

    @Query("select d.id from Drop d where d.status = :status and d.startsAt <= :now order by d.startsAt")
    fun findIdsToStart(now: Instant, status: DropStatus = DropStatus.SCHEDULED): List<Long>

    @Query("select d.id from Drop d where d.status = :status")
    fun findIdsWithStatus(status: DropStatus): List<Long>

    @Query(
        "select d.id from Drop d where d.status = :status and d.remindersSentAt is null and d.startsAt <= :until " +
            "order by d.startsAt",
    )
    fun findIdsToRemind(until: Instant, status: DropStatus = DropStatus.SCHEDULED): List<Long>

    /** For the LIVE badge in the menu. */
    @Query("select d from Drop d where d.status = :status order by d.startsAt")
    fun findWithStatus(status: DropStatus): List<Drop>
}
