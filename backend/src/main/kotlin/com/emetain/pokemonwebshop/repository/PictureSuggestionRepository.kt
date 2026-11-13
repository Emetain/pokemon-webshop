package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.PictureSuggestion
import com.emetain.pokemonwebshop.domain.ReleaseSuggestion
import com.emetain.pokemonwebshop.domain.SuggestionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PictureSuggestionRepository : JpaRepository<PictureSuggestion, Long> {

    /** Cards that have a suggestion already: open, or rejected by the admin. The search leaves them alone. */
    @Query("select s.card.id from PictureSuggestion s where s.game = :game and s.card is not null and s.status <> 'ACCEPTED'")
    fun findCardIdsWithSuggestion(game: Game): List<Long>

    @Query("select s.product.id from PictureSuggestion s where s.game = :game and s.product is not null and s.status <> 'ACCEPTED'")
    fun findProductIdsWithSuggestion(game: Game): List<Long>

    /** Candidates come in batches (see the entity): a page can't fetch a collection with it. */
    @EntityGraph(attributePaths = ["card", "card.set", "product", "product.cardSet"])
    @Query("select s from PictureSuggestion s where s.status = :status and (:game is null or s.game = :game) order by s.game, s.id")
    fun findByStatus(status: SuggestionStatus, game: Game?, pageable: Pageable): Page<PictureSuggestion>

    @EntityGraph(attributePaths = ["card", "card.set", "product", "product.cardSet", "candidates"])
    @Query("select s from PictureSuggestion s where s.id = :id")
    fun findWithCandidates(id: Long): PictureSuggestion?

    fun countByGameAndStatus(game: Game, status: SuggestionStatus): Long
}

interface ReleaseSuggestionRepository : JpaRepository<ReleaseSuggestion, Long> {
    fun findByGameAndNameKey(game: Game, nameKey: String): ReleaseSuggestion?
}
