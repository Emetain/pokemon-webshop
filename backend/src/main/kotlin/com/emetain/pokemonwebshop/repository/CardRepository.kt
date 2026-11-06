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
}
