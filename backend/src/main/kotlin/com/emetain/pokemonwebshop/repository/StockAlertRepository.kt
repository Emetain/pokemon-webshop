package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.StockAlert
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface StockAlertRepository : JpaRepository<StockAlert, Long> {
    fun findByProductIdAndEmail(productId: Long, email: String): StockAlert?

    @Query("select a from StockAlert a where a.product.id = :productId and a.notifiedAt is null order by a.createdAt")
    fun findWaiting(productId: Long): List<StockAlert>

    @Query("select count(a) from StockAlert a where a.product.id = :productId and a.notifiedAt is null")
    fun countWaiting(productId: Long): Long
}
