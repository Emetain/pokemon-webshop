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

    /** For the sitemap: every set has a page with its cards. */
    @Query("select new com.emetain.pokemonwebshop.repository.SetRow(s.game, s.code) from CardSet s")
    fun sitemapRows(): List<SetRow>

    /** Sets the card sources already list but that aren't out yet, for the release calendar suggestions. */
    fun findByReleaseDateAfter(date: java.time.LocalDate): List<CardSet>
}
