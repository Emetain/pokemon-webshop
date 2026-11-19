package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.config.ShopAccess
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

/**
 * Who may order. While the site is online without the preview login but the shop isn't open yet
 * (`SHOP_ACCESS=unlisted`), only admins: the owner can still try a payment with Stripe's test mode, and a visitor can't
 * "pay" with test card 4242 and get an order. In the preview (behind the login) and the open shop, everyone.
 */
@Component
class SalesGate(properties: AppProperties) {

    private val access = properties.shopAccess

    fun canOrder(viewer: User?): Boolean = access != ShopAccess.UNLISTED || viewer?.role == Role.ADMIN

    fun requireCanOrder(viewer: User?) {
        if (!canOrder(viewer)) {
            throw ApiException(HttpStatus.FORBIDDEN, "shop_not_open", "De webshop is nog niet open. Bestellen kan nog niet.")
        }
    }
}
