package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.CardmarketSingle
import com.emetain.pokemonwebshop.domain.Game
import org.springframework.data.jpa.repository.JpaRepository

interface CardmarketSingleRepository : JpaRepository<CardmarketSingle, Long> {
    fun findAllByGameAndExpansionIdIn(game: Game, expansionIds: Collection<Long>): List<CardmarketSingle>

    fun findAllByGameAndExpansionIdOrderByProductId(game: Game, expansionId: Long): List<CardmarketSingle>

    fun countByGame(game: Game): Long
}
