package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.PremiumCheckoutRequest
import com.emetain.pokemonwebshop.dto.RedirectDto
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RateLimited
import com.emetain.pokemonwebshop.security.RequiresLogin
import com.emetain.pokemonwebshop.service.PremiumService
import com.emetain.pokemonwebshop.service.PremiumStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Premium (phase 11): the status, Stripe's payment page and Stripe's customer portal. */
@RestController
@RequestMapping("/v1/premium")
@RequiresLogin
class PremiumController(
    private val premiumService: PremiumService,
) {

    @GetMapping
    fun status(@CurrentUser user: User): PremiumStatus = premiumService.status(user)

    @PostMapping("/checkout")
    @RateLimited("checkout")
    fun checkout(@CurrentUser user: User, @RequestBody request: PremiumCheckoutRequest): RedirectDto =
        RedirectDto(premiumService.startCheckout(user, request.plan, request.consent))

    @PostMapping("/portal")
    @RateLimited("checkout")
    fun portal(@CurrentUser user: User): RedirectDto = RedirectDto(premiumService.portal(user))
}
