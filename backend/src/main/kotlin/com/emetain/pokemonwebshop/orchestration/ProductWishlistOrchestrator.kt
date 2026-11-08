package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.ProductPageDto
import com.emetain.pokemonwebshop.repository.ProductSort
import com.emetain.pokemonwebshop.repository.ProductSpecifications
import com.emetain.pokemonwebshop.service.ProductWishlistService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductWishlistOrchestrator(
    private val wishlistService: ProductWishlistService,
    private val products: ProductOrchestrator,
) {

    /** Products that can be bought first, so the wishlist doubles as a shopping list. */
    @Transactional(readOnly = true)
    fun products(user: User, page: Int, size: Int): ProductPageDto {
        val filters = listOf(
            ProductSpecifications.withStatus(ProductStatus.ACTIVE)!!,
            ProductSpecifications.onWishlistOf(user.id),
            ProductSpecifications.sortedBy(ProductSort.FEATURED),
        )
        return products.pageDto(products.page(filters, page, size), user)
    }

    fun add(user: User, productId: Long) = retryingOnConflict { wishlistService.add(user, productId) }

    fun remove(user: User, productId: Long) = wishlistService.remove(user.id, productId)
}
