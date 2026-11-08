package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.ProductWishlistEntry
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.repository.ProductWishlistEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** Adding a product twice or removing it twice is fine, so a double click can't fail. */
@Service
class ProductWishlistService(
    private val wishlistRepository: ProductWishlistEntryRepository,
    private val productRepository: ProductRepository,
    private val clock: Clock,
) {

    @Transactional
    fun add(user: User, productId: Long) {
        if (wishlistRepository.existsByUserIdAndProductId(user.id, productId)) return
        val product = productRepository.findById(productId)
            .filter { it.status == ProductStatus.ACTIVE }
            .orElseThrow { ApiException.notFound("We konden dit product niet vinden.") }
        wishlistRepository.saveAndFlush(ProductWishlistEntry(user, product, clock.instant()))
    }

    @Transactional
    fun remove(userId: Long, productId: Long) {
        wishlistRepository.findByUserIdAndProductId(userId, productId)?.let(wishlistRepository::delete)
    }

    @Transactional(readOnly = true)
    fun wished(userId: Long, productIds: Collection<Long>): Set<Long> =
        if (productIds.isEmpty()) emptySet() else wishlistRepository.findWishedProductIds(userId, productIds).toSet()
}
