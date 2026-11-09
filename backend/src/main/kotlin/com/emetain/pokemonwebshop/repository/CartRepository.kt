package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Cart
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface CartRepository : JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = ["items", "items.product", "items.product.stock"])
    fun findByUserId(userId: Long): Cart?

    @EntityGraph(attributePaths = ["items", "items.product", "items.product.stock"])
    fun findByGuestTokenHash(guestTokenHash: String): Cart?

    @EntityGraph(attributePaths = ["items", "items.product", "items.product.stock"])
    fun findWithItemsById(id: Long): Cart?

    @Modifying
    @Query("delete from CartItem i where i.cart.id in (select c.id from Cart c where c.user is null and c.updatedAt < :before)")
    fun deleteGuestItemsBefore(before: Instant): Int

    @Modifying
    @Query("delete from Cart c where c.user is null and c.updatedAt < :before")
    fun deleteGuestCartsBefore(before: Instant): Int
}
