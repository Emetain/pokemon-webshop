package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.CollectionEntry
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface CollectionEntryRepository : JpaRepository<CollectionEntry, Long> {
    fun findByIdAndUserId(id: Long, userId: Long): CollectionEntry?

    /** Locks the entry, so two additions at the same moment both count. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CollectionEntry e where e.user.id = :userId and e.card.id = :cardId and e.variant = :variant")
    fun findForUpdate(userId: Long, cardId: Long, variant: String): CollectionEntry?

    @Query("select e from CollectionEntry e where e.user.id = :userId and e.card.id in :cardIds")
    fun findAllByUserAndCards(userId: Long, cardIds: Collection<Long>): List<CollectionEntry>

    @Query("select distinct e.card.id from CollectionEntry e where e.user.id = :userId and e.card.id in :cardIds")
    fun findOwnedCardIds(userId: Long, cardIds: Collection<Long>): List<Long>

    /** Different cards can share a number, such as alternate arts. For normal progress they count once. */
    @Query(
        "select c.set.id as setId, count(distinct c.number) as count from CollectionEntry e join e.card c " +
            "where e.user.id = :userId group by c.set.id",
    )
    fun countOwnedNumbersPerSet(userId: Long): List<SetCount>

    /** Only variants the card still has, so a source that drops one can't push progress past 100%. */
    @Query(
        "select c.set.id as setId, count(e.id) as count from CollectionEntry e join e.card c join c.variants v " +
            "where e.user.id = :userId and v = e.variant group by c.set.id",
    )
    fun countOwnedVariantsPerSet(userId: Long): List<SetCount>

    @Query(
        "select count(distinct c.number) from CollectionEntry e join e.card c " +
            "where e.user.id = :userId and c.set.id = :setId",
    )
    fun countOwnedNumbersInSet(userId: Long, setId: Long): Long

    @Query(
        "select count(e.id) from CollectionEntry e join e.card c join c.variants v " +
            "where e.user.id = :userId and c.set.id = :setId and v = e.variant",
    )
    fun countOwnedVariantsInSet(userId: Long, setId: Long): Long

    @Query(
        "select new com.emetain.pokemonwebshop.repository.OwnedLine(c.id, c.game, c.set.id, e.variant, e.quantity) " +
            "from CollectionEntry e join e.card c where e.user.id = :userId",
    )
    fun findOwnedLines(userId: Long): List<OwnedLine>

    @Query(
        "select new com.emetain.pokemonwebshop.repository.OwnedLine(c.id, c.game, c.set.id, e.variant, e.quantity) " +
            "from CollectionEntry e join e.card c where e.user.id = :userId and c.set.id = :setId",
    )
    fun findOwnedLinesInSet(userId: Long, setId: Long): List<OwnedLine>

    @Query("select distinct e.user.id from CollectionEntry e")
    fun findUserIdsWithEntries(): List<Long>

    /** The cards in the most collections. Without an image they can't go in the home binder. */
    @Query(
        "select c.id from CollectionEntry e join e.card c " +
            "where c.imageSmallUrl is not null or c.customImageUrl is not null " +
            "group by c.id, c.sortPriceCents " +
            "order by count(distinct e.user.id) desc, c.sortPriceCents desc nulls last, c.id",
    )
    fun findMostCollectedCardIds(pageable: Pageable): List<Long>
}
