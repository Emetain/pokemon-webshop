package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CardListRequest
import com.emetain.pokemonwebshop.dto.CardPageDto
import com.emetain.pokemonwebshop.dto.ProductSummaryDto
import com.emetain.pokemonwebshop.dto.WantedProductDto
import com.emetain.pokemonwebshop.dto.WishlistMaxPriceRequest
import com.emetain.pokemonwebshop.repository.CardSort
import com.emetain.pokemonwebshop.repository.CardSpecifications
import com.emetain.pokemonwebshop.repository.CardWishlistEntryRepository
import com.emetain.pokemonwebshop.repository.CollectionEntryRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.service.CardWishlistService
import com.emetain.pokemonwebshop.service.ProductRules
import com.emetain.pokemonwebshop.service.ProductWishlistService
import com.emetain.pokemonwebshop.service.WantedMatch
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CardWishlistOrchestrator(
    private val wishlistService: CardWishlistService,
    private val cardList: CardListOrchestrator,
    private val productRepository: ProductRepository,
    private val productWishlistService: ProductWishlistService,
    private val wishlistRepository: CardWishlistEntryRepository,
    private val collectionEntryRepository: CollectionEntryRepository,
) {

    /**
     * Numbers mean nothing across sets, so these start with the most valuable card. Each card comes with the
     * customer's maximum for it, which they can change on this page (2026-11-24).
     */
    @Transactional(readOnly = true)
    fun cards(user: User, request: CardListRequest): CardPageDto {
        val sort = CardSort.fromParam(request.sort.filterValue()) ?: CardSort.PRICE_HIGHEST
        val filters = cardList.filters(request, user) +
            CardSpecifications.onWishlistOf(user.id) +
            CardSpecifications.sortedBy(sort)
        val page = cardList.page(filters, request, user)
        if (page.items.isEmpty()) return page
        val maxPrices = wishlistRepository.findAllByUserIdAndCardIdIn(user.id, page.items.map { it.id })
            .associate { it.card.id to it.maxPriceCents }
        return page.copy(items = page.items.map { it.copy(wishlistMaxPriceCents = maxPrices[it.id]) })
    }

    /**
     * The cards on the wishlist that the shop sells right now, for the top of the home page (2026-11-23). One product
     * per card, the cheapest copy, so three copies of one card don't fill the row; its product page shows the other
     * copies. The card whose copy arrived last comes first, so a card that just came in is at the front. Each comes
     * with the colour of its frame ([WantedMatch]).
     */
    @Transactional(readOnly = true)
    fun forSale(user: User, limit: Int): List<WantedProductDto> {
        val copies = productRepository.findBuyableWantedBy(user.id, PageRequest.of(0, MAX_COPIES_READ))
        // groupBy keeps the order in which each card first appears: its newest arrival.
        val products = copies.groupBy { it.card!!.id }.values
            .map { ofOneCard -> ofOneCard.minWith(compareBy<Product> { ProductRules.shownPriceCents(it) }.thenBy { it.id }) }
            .take(limit.coerceIn(1, MAX_FOR_SALE))
        if (products.isEmpty()) return emptyList()
        val cardIds = products.map { it.card!!.id }
        val wished = productWishlistService.wished(user.id, products.map(Product::id))
        val maxPrices = wishlistRepository.findAllByUserIdAndCardIdIn(user.id, cardIds).associate { it.card.id to it.maxPriceCents }
        val owned = collectionEntryRepository.findOwnedCardIds(user.id, cardIds).toSet()
        return products.map { product ->
            val cardId = product.card!!.id
            val maxPrice = maxPrices[cardId]
            WantedProductDto(
                product = ProductSummaryDto.from(product, wished.contains(product.id)),
                match = WantedMatch.of(ProductRules.shownPriceCents(product), maxPrice, cardId in owned),
                maxPriceCents = maxPrice,
            )
        }
    }

    fun setMaxPrice(user: User, cardId: Long, request: WishlistMaxPriceRequest) =
        retryingOnConflict { wishlistService.setMaxPrice(user, cardId, request.maxPriceCents) }

    fun add(user: User, cardId: Long) = retryingOnConflict { wishlistService.add(user, cardId) }

    fun remove(user: User, cardId: Long) = wishlistService.remove(user.id, cardId)

    private companion object {
        const val MAX_FOR_SALE = 24

        /** Enough copies for a full row, also when some cards are in the shop many times. */
        const val MAX_COPIES_READ = 200
    }
}
