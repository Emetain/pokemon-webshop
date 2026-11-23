package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CardListRequest
import com.emetain.pokemonwebshop.dto.CardPageDto
import com.emetain.pokemonwebshop.dto.WantedProductDto
import com.emetain.pokemonwebshop.dto.WishlistMaxPriceRequest
import com.emetain.pokemonwebshop.orchestration.CardWishlistOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RequiresLogin
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/wishlist/cards")
@RequiresLogin
class CardWishlistController(
    private val wishlistOrchestrator: CardWishlistOrchestrator,
) {

    @GetMapping
    fun cards(@CurrentUser user: User, @ParameterObject request: CardListRequest): CardPageDto =
        wishlistOrchestrator.cards(user, request)

    /**
     * The cards on the wishlist that are for sale right now: one product per card, the newest arrival first, each with
     * the colour of its frame on the home page.
     */
    @GetMapping("/for-sale")
    fun forSale(@CurrentUser user: User, @RequestParam(defaultValue = "12") limit: Int): List<WantedProductDto> =
        wishlistOrchestrator.forSale(user, limit)

    /** The most the customer wants to pay for this card in the shop. Puts the card on the wishlist if it isn't yet. */
    @PutMapping("/{cardId}/max-price")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setMaxPrice(@CurrentUser user: User, @PathVariable cardId: Long, @RequestBody request: WishlistMaxPriceRequest) =
        wishlistOrchestrator.setMaxPrice(user, cardId, request)

    @PutMapping("/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun add(@CurrentUser user: User, @PathVariable cardId: Long) =
        wishlistOrchestrator.add(user, cardId)

    @DeleteMapping("/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(@CurrentUser user: User, @PathVariable cardId: Long) =
        wishlistOrchestrator.remove(user, cardId)
}
