package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CartDto
import com.emetain.pokemonwebshop.dto.CartItemRequest
import com.emetain.pokemonwebshop.dto.CartQuantityRequest
import com.emetain.pokemonwebshop.orchestration.CartOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** The cart of the logged-in customer, or of the guest behind the `cart` cookie. `country` decides the shipping costs. */
@RestController
@RequestMapping("/v1/cart")
class CartController(
    private val cartOrchestrator: CartOrchestrator,
) {

    @GetMapping
    fun get(@CurrentUser viewer: User?, request: HttpServletRequest, @RequestParam(required = false) country: String?): CartDto =
        cartOrchestrator.get(viewer, request, country)

    @PostMapping("/items")
    fun add(
        @CurrentUser viewer: User?,
        request: HttpServletRequest,
        @Valid @RequestBody body: CartItemRequest,
        @RequestParam(required = false) country: String?,
    ): ResponseEntity<CartDto> = cartOrchestrator.add(viewer, request, body, country)

    @PutMapping("/items/{productId}")
    fun setQuantity(
        @CurrentUser viewer: User?,
        request: HttpServletRequest,
        @PathVariable productId: Long,
        @Valid @RequestBody body: CartQuantityRequest,
        @RequestParam(required = false) country: String?,
    ): CartDto = cartOrchestrator.setQuantity(viewer, request, productId, body.quantity, country)

    @DeleteMapping("/items/{productId}")
    fun remove(
        @CurrentUser viewer: User?,
        request: HttpServletRequest,
        @PathVariable productId: Long,
        @RequestParam(required = false) country: String?,
    ): CartDto = cartOrchestrator.remove(viewer, request, productId, country)
}
