package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.BinderSelection
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface BinderSelectionRepository : JpaRepository<BinderSelection, Long> {
    fun countByUserId(userId: Long): Long

    fun existsByUserIdAndCardId(userId: Long, cardId: Long): Boolean

    fun findByUserIdAndCardId(userId: Long, cardId: Long): BinderSelection?

    @EntityGraph(attributePaths = ["card", "card.set"])
    fun findAllByUserIdOrderById(userId: Long): List<BinderSelection>

    @EntityGraph(attributePaths = ["card", "card.set"])
    fun findAllByUserIdOrderById(userId: Long, pageable: Pageable): Page<BinderSelection>
}
