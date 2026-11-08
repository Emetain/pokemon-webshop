package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** The photos of a product. The first one is the main image in lists. */
@Service
class ProductImageService(
    private val productRepository: ProductRepository,
    private val mediaStorage: MediaStorage,
    private val clock: Clock,
) {

    @Transactional
    fun add(productId: Long, bytes: ByteArray): Product {
        val product = find(productId)
        if (product.images.size >= MAX_IMAGES) {
            throw ApiException.validation("Een product kan maximaal $MAX_IMAGES afbeeldingen hebben.")
        }
        val format = ImageFormat.detect(bytes) ?: throw ApiException.validation("Kies een afbeelding in JPG, PNG of WebP.")
        product.images += mediaStorage.store(bytes, format)
        touch(product)
        return product
    }

    @Transactional
    fun remove(productId: Long, index: Int): Product {
        val product = find(productId)
        if (index !in product.images.indices) throw ApiException.notFound("Deze afbeelding bestaat niet.")
        val url = product.images.removeAt(index)
        productRepository.flush()
        if (productRepository.countUsingImage(url) == 0L) mediaStorage.delete(url)
        touch(product)
        return product
    }

    /** [order] holds the current URLs in their new order: nothing added, nothing left out. */
    @Transactional
    fun reorder(productId: Long, order: List<String>): Product {
        val product = find(productId)
        if (order.size != product.images.size || order.toSet() != product.images.toSet()) {
            throw ApiException.validation("De volgorde past niet bij de afbeeldingen van dit product. Herlaad de pagina.")
        }
        product.images.clear()
        product.images += order
        touch(product)
        return product
    }

    private fun touch(product: Product) {
        product.updatedAt = clock.instant()
        product.editedByAdmin = true
    }

    private fun find(productId: Long): Product =
        productRepository.findWithDetailsById(productId) ?: throw ApiException.notFound("We konden dit product niet vinden.")

    private companion object {
        const val MAX_IMAGES = 12
    }
}
