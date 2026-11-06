package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CardListRequest
import com.emetain.pokemonwebshop.dto.CardPageDto
import com.emetain.pokemonwebshop.orchestration.CardWishlistOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RequiresLogin
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
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

    @PutMapping("/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun add(@CurrentUser user: User, @PathVariable cardId: Long) =
        wishlistOrchestrator.add(user, cardId)

    @DeleteMapping("/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(@CurrentUser user: User, @PathVariable cardId: Long) =
        wishlistOrchestrator.remove(user, cardId)
}
