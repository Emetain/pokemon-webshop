package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CardListRequest
import com.emetain.pokemonwebshop.dto.CardPageDto
import com.emetain.pokemonwebshop.dto.ProductSummaryDto
import com.emetain.pokemonwebshop.repository.CardSort
import com.emetain.pokemonwebshop.repository.CardSpecifications
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.service.CardWishlistService
import com.emetain.pokemonwebshop.service.ProductRules
import com.emetain.pokemonwebshop.service.ProductWishlistService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CardWishlistOrchestrator(
    private val wishlistService: CardWishlistService,
    private val cardList: CardListOrchestrator,
    private val productRepository: ProductRepository,
    private val productWishlistService: ProductWishlistService,
) {

    /** Numbers mean nothing across sets, so these start with the most valuable card. */
    @Transactional(readOnly = true)
    fun cards(user: User, request: CardListRequest): CardPageDto {
        val sort = CardSort.fromParam(request.sort.filterValue()) ?: CardSort.PRICE_HIGHEST
        val filters = cardList.filters(request, user) +
            CardSpecifications.onWishlistOf(user.id) +
            CardSpecifications.sortedBy(sort)
        return cardList.page(filters, request, user)
    }

    /**
     * The cards on the wishlist that the shop sells right now, for the top of the home page (2026-11-23). One product
     * per card, the cheapest copy, so three copies of one card don't fill the row; its product page shows the other
     * copies. The card whose copy arrived last comes first, so a card that just came in is at the front.
     */
    @Transactional(readOnly = true)
    fun forSale(user: User, limit: Int): List<ProductSummaryDto> {
        val copies = productRepository.findBuyableWantedBy(user.id, PageRequest.of(0, MAX_COPIES_READ))
        // groupBy keeps the order in which each card first appears: its newest arrival.
        val products = copies.groupBy { it.card!!.id }.values
            .map { ofOneCard -> ofOneCard.minWith(compareBy<Product> { ProductRules.shownPriceCents(it) }.thenBy { it.id }) }
            .take(limit.coerceIn(1, MAX_FOR_SALE))
        val wished = productWishlistService.wished(user.id, products.map(Product::id))
        return products.map { ProductSummaryDto.from(it, wished.contains(it.id)) }
    }

    fun add(user: User, cardId: Long) = retryingOnConflict { wishlistService.add(user, cardId) }

    fun remove(user: User, cardId: Long) = wishlistService.remove(user.id, cardId)

    private companion object {
        const val MAX_FOR_SALE = 24

        /** Enough copies for a full row, also when some cards are in the shop many times. */
        const val MAX_COPIES_READ = 200
    }
}
