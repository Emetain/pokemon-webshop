package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CheckoutOptionsDto
import com.emetain.pokemonwebshop.dto.CheckoutStartDto
import com.emetain.pokemonwebshop.dto.StartCheckoutRequest
import com.emetain.pokemonwebshop.orchestration.CheckoutOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RateLimited
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/checkout")
class CheckoutController(
    private val checkoutOrchestrator: CheckoutOrchestrator,
) {

    @GetMapping("/options")
    fun options(): CheckoutOptionsDto = checkoutOrchestrator.options()

    /** Reserves the stock and returns the Stripe page to send the customer to. */
    @PostMapping
    @RateLimited("checkout")
    @ResponseStatus(HttpStatus.CREATED)
    fun start(
        @CurrentUser viewer: User?,
        request: HttpServletRequest,
        @Valid @RequestBody body: StartCheckoutRequest,
    ): CheckoutStartDto = checkoutOrchestrator.start(viewer, request, body)
}
