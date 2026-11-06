package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.Game
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query

interface CardRepository : JpaRepository<Card, Long>, JpaSpecificationExecutor<Card> {
    fun findAllByGameAndExternalIdIn(game: Game, externalIds: Collection<String>): List<Card>

    @EntityGraph(attributePaths = ["set"])
    override fun findAll(spec: Specification<Card>?, pageable: Pageable): Page<Card>

    @Query("select c from Card c join fetch c.set where c.id = :id")
    fun findWithSetById(id: Long): Card?

    @Query("select distinct c.rarity from Card c where c.set.id = :setId and c.rarity is not null order by c.rarity")
    fun findRaritiesInSet(setId: Long): List<String>

    @Query("select distinct v from Card c join c.variants v where c.set.id = :setId")
    fun findVariantsInSet(setId: Long): List<String>

    @Query("select distinct c.rarity from Card c where c.game = :game and c.rarity is not null order by c.rarity")
    fun findRaritiesInGame(game: Game): List<String>

    @Query("select distinct c.category from Card c where c.set.id = :setId and c.category is not null order by c.category")
    fun findCategoriesInSet(setId: Long): List<String>

    @Query("select distinct v from Card c join c.colors v where c.set.id = :setId order by v")
    fun findColorsInSet(setId: Long): List<String>

    @Query("select distinct c.category from Card c where c.game = :game and c.category is not null order by c.category")
    fun findCategoriesInGame(game: Game): List<String>

    @Query("select distinct v from Card c join c.colors v where c.game = :game order by v")
    fun findColorsInGame(game: Game): List<String>

    fun countByGame(game: Game): Long

    @Query("select c from Card c join fetch c.set where c.id in :ids")
    fun findAllWithSetByIdIn(ids: Collection<Long>): List<Card>

    @Query("select c.set.id as setId, c.game as game, count(distinct c.number) as count from Card c group by c.set.id, c.game")
    fun countNumbersPerSet(): List<SetNumbers>

    @Query("select c.set.id as setId, count(v) as count from Card c join c.variants v group by c.set.id")
    fun countVariantsPerSet(): List<SetCount>

    @Query("select count(distinct c.number) from Card c where c.set.id = :setId")
    fun countNumbersInSet(setId: Long): Long

    @Query("select count(v) from Card c join c.variants v where c.set.id = :setId")
    fun countVariantsInSet(setId: Long): Long

    @Query(
        "select c from Card c join fetch c.set " +
            "where c.sortPriceCents is not null and c.imageSmallUrl is not null order by c.sortPriceCents desc, c.id",
    )
    fun findMostExpensive(pageable: Pageable): List<Card>

    @Query(
        "select c from Card c join fetch c.set where c.game = :game " +
            "and c.sortPriceCents is not null and c.imageSmallUrl is not null order by c.sortPriceCents desc, c.id",
    )
    fun findMostExpensiveOfGame(game: Game, pageable: Pageable): List<Card>

    @EntityGraph(attributePaths = ["set"])
    @Query(
        "select c from Card c where c.id in (select e.card.id from CollectionEntry e where e.user.id = :userId) " +
            "order by c.sortPriceCents desc nulls last, c.id",
    )
    fun findOwnedMostExpensiveFirst(userId: Long, pageable: Pageable): Page<Card>

    @Query(
        "select new com.emetain.pokemonwebshop.repository.OwnedDexCard(d, c.id, c.game, s.code, c.imageSmallUrl, c.sortPriceCents) " +
            "from Card c join c.set s join c.dexNumbers d " +
            "where c.id in (select e.card.id from CollectionEntry e where e.user.id = :userId)",
    )
    fun findOwnedDexCards(userId: Long): List<OwnedDexCard>

    @Query("select count(c) from Card c join c.dexNumbers d where d = :dexNumber")
    fun countByDexNumber(dexNumber: Int): Long

    @Query(
        "select count(distinct c.id) from Card c join c.dexNumbers d " +
            "where d = :dexNumber and c.id in (select e.card.id from CollectionEntry e where e.user.id = :userId)",
    )
    fun countOwnedByDexNumber(userId: Long, dexNumber: Int): Long

    @Query(
        "select distinct c.rarity from Card c join c.dexNumbers d " +
            "where d = :dexNumber and c.rarity is not null order by c.rarity",
    )
    fun findRaritiesForDexNumber(dexNumber: Int): List<String>

    @Query("select distinct v from Card c join c.dexNumbers d join c.variants v where d = :dexNumber")
    fun findVariantsForDexNumber(dexNumber: Int): List<String>

    @Query("select distinct v from Card c join c.dexNumbers d join c.colors v where d = :dexNumber order by v")
    fun findColorsForDexNumber(dexNumber: Int): List<String>
}
