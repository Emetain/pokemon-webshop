package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.GuestAccountRequest
import com.emetain.pokemonwebshop.dto.OrderDto
import com.emetain.pokemonwebshop.dto.OrderPageDto
import com.emetain.pokemonwebshop.dto.OrderTokenRequest
import com.emetain.pokemonwebshop.dto.UserDto
import com.emetain.pokemonwebshop.orchestration.OrderOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RateLimited
import com.emetain.pokemonwebshop.security.RequiresLogin
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** One order, for its owner or for a guest with the order's key from the success page or a mail. */
@RestController
@RequestMapping("/v1/orders")
class OrderController(
    private val orderOrchestrator: OrderOrchestrator,
) {

    @GetMapping("/{number}")
    fun view(
        @CurrentUser viewer: User?,
        @PathVariable number: String,
        @RequestParam(required = false) token: String?,
    ): OrderDto = orderOrchestrator.view(number, token, viewer)

    /** The customer went back from the payment page: the stock goes back right away instead of after 15 minutes. */
    @PostMapping("/{number}/cancel")
    fun cancel(
        @CurrentUser viewer: User?,
        @PathVariable number: String,
        @RequestBody body: OrderTokenRequest,
    ): OrderDto = orderOrchestrator.cancelPending(number, body.token.ifBlank { null }, viewer)

    @PostMapping("/{number}/account")
    @RateLimited("order-account")
    fun createAccount(
        @PathVariable number: String,
        @Valid @RequestBody body: GuestAccountRequest,
        request: HttpServletRequest,
    ): ResponseEntity<UserDto> = orderOrchestrator.createAccount(number, body, request)
}

@RestController
@RequestMapping("/v1/account/orders")
@RequiresLogin
class AccountOrderController(
    private val orderOrchestrator: OrderOrchestrator,
) {

    @GetMapping
    fun list(@CurrentUser user: User, @RequestParam(defaultValue = "0") page: Int): OrderPageDto =
        orderOrchestrator.mine(user, page)

    @GetMapping("/{number}")
    fun get(@CurrentUser user: User, @PathVariable number: String): OrderDto =
        orderOrchestrator.myOrder(user, number)
}
