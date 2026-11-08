package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.dto.CardOfferDto
import com.emetain.pokemonwebshop.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Which cards of the catalog the shop sells right now, as singles or slabs in stock. */
@Service
class CardOfferService(
    private val productRepository: ProductRepository,
) {

    @Transactional(readOnly = true)
    fun cheapestPerCard(cardIds: Collection<Long>): Map<Long, CardOfferDto> =
        buyable(cardIds).groupBy { it.card!!.id }.mapValues { (_, products) ->
            val cheapest = products.first()
            CardOfferDto(cheapest.slug, ProductRules.shownPriceCents(cheapest)!!, products.size)
        }

    /** Cheapest first. */
    @Transactional(readOnly = true)
    fun buyable(cardIds: Collection<Long>): List<Product> =
        if (cardIds.isEmpty()) {
            emptyList()
        } else {
            productRepository.findBuyableForCards(cardIds).sortedWith(
                compareBy<Product> { ProductRules.shownPriceCents(it) }.thenBy { it.id },
            )
        }
}
