package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CardListRequest
import com.emetain.pokemonwebshop.dto.CardPageDto
import com.emetain.pokemonwebshop.repository.CardSort
import com.emetain.pokemonwebshop.repository.CardSpecifications
import com.emetain.pokemonwebshop.service.CardWishlistService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CardWishlistOrchestrator(
    private val wishlistService: CardWishlistService,
    private val cardList: CardListOrchestrator,
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

    fun add(user: User, cardId: Long) = retryingOnConflict { wishlistService.add(user, cardId) }

    fun remove(user: User, cardId: Long) = wishlistService.remove(user.id, cardId)
}
