package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.ExtraBinder
import com.emetain.pokemonwebshop.domain.ExtraBinderEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ExtraBinderRepository : JpaRepository<ExtraBinder, Long> {

    fun findAllByUserIdOrderByCreatedAtAscIdAsc(userId: Long): List<ExtraBinder>

    fun findByIdAndUserId(id: Long, userId: Long): ExtraBinder?

    fun countByUserId(userId: Long): Long
}

interface ExtraBinderEntryRepository : JpaRepository<ExtraBinderEntry, Long> {

    @Query(
        "select e from ExtraBinderEntry e join fetch e.card c join fetch c.set where e.binder.id = :binderId " +
            "order by c.set.releaseDate desc nulls last, c.sortKey, e.variant",
    )
    fun findAllOfBinder(binderId: Long): List<ExtraBinderEntry>

    @Query("select e from ExtraBinderEntry e join fetch e.binder where e.binder.user.id = :userId and e.card.id = :cardId")
    fun findAllOfUserAndCard(userId: Long, cardId: Long): List<ExtraBinderEntry>

    @Query("select e from ExtraBinderEntry e join fetch e.card c join fetch c.set where e.binder.user.id = :userId")
    fun findAllOfUser(userId: Long): List<ExtraBinderEntry>

    fun findByBinderIdAndCardIdAndVariant(binderId: Long, cardId: Long, variant: String): ExtraBinderEntry?

    fun findByIdAndBinderId(id: Long, binderId: Long): ExtraBinderEntry?

    fun countByBinderId(binderId: Long): Long

    fun deleteAllByBinderId(binderId: Long)
}
