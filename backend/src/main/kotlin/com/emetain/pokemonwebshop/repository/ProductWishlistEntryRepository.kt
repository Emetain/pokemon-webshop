package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.ProductWishlistEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProductWishlistEntryRepository : JpaRepository<ProductWishlistEntry, Long> {
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean

    fun findByUserIdAndProductId(userId: Long, productId: Long): ProductWishlistEntry?

    @Query("select e.product.id from ProductWishlistEntry e where e.user.id = :userId and e.product.id in :productIds")
    fun findWishedProductIds(userId: Long, productIds: Collection<Long>): List<Long>
}
