package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.ProductPageDto
import com.emetain.pokemonwebshop.orchestration.ProductWishlistOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RequiresLogin
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/wishlist/products")
@RequiresLogin
class ProductWishlistController(
    private val wishlistOrchestrator: ProductWishlistOrchestrator,
) {

    @GetMapping
    fun products(
        @CurrentUser user: User,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "24") size: Int,
    ): ProductPageDto = wishlistOrchestrator.products(user, page, size)

    @PutMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun add(@CurrentUser user: User, @PathVariable productId: Long) =
        wishlistOrchestrator.add(user, productId)

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(@CurrentUser user: User, @PathVariable productId: Long) =
        wishlistOrchestrator.remove(user, productId)
}
