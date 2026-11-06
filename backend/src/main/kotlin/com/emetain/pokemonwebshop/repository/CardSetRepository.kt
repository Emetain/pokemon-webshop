package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.Game
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CardSetRepository : JpaRepository<CardSet, Long> {
    fun findByGameAndExternalId(game: Game, externalId: String): CardSet?

    fun findByGameAndCode(game: Game, code: String): CardSet?

    @Query(
        "select s from CardSet s where s.game = :game " +
            "order by s.releaseDate desc nulls last, s.sortOrder desc, s.name",
    )
    fun findAllNewestFirst(game: Game): List<CardSet>

    fun countByGame(game: Game): Long
}
