package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.StockMovement
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface StockMovementRepository : JpaRepository<StockMovement, Long> {

    @EntityGraph(attributePaths = ["adminUser"])
    @Query("select m from StockMovement m where m.product.id = :productId order by m.createdAt desc, m.id desc")
    fun findByProduct(productId: Long, pageable: Pageable): Page<StockMovement>

    @EntityGraph(attributePaths = ["adminUser", "product"])
    @Query("select m from StockMovement m order by m.createdAt desc, m.id desc")
    fun findLatest(pageable: Pageable): Page<StockMovement>

    fun existsByProductId(productId: Long): Boolean
}
