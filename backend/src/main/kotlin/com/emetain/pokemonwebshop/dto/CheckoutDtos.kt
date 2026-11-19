package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.ShipmentPreference
import jakarta.validation.Valid
import jakarta.validation.constraints.Size

data class CheckoutCountryDto(
    val country: String,
    val priceCents: Long,
    val freeFromCents: Long?,
    val deliveryTime: String,
    val paymentMethods: List<String>,
)

data class CheckoutOptionsDto(
    /** False while no Stripe key is set: the checkout then says online payment isn't available yet. */
    val paymentsEnabled: Boolean,
    /** False while the site is online but the shop isn't open yet, for everyone but admins (`SHOP_ACCESS=unlisted`). */
    val canOrder: Boolean = true,
    val countries: List<CheckoutCountryDto>,
    val reservationMinutes: Long,
)

data class StartCheckoutRequest(
    /** A saved address of the logged-in customer. Otherwise [address] is used. */
    val addressId: Long? = null,

    @field:Valid
    val address: AddressRequest? = null,

    /** Saves [address] in the account of a logged-in customer. */
    val saveAddress: Boolean = false,

    /** Guests only: a logged-in customer orders in the name of the profile. */
    @field:Size(max = 100, message = "Je naam mag maximaal 100 tekens lang zijn.")
    val name: String? = null,

    /** Guests only. */
    @field:Size(max = 254, message = "Dit e-mailadres is te lang.")
    val email: String? = null,

    val shipmentPreference: ShipmentPreference? = null,

    val acceptTerms: Boolean = false,

    /** A discount code. The order fails with `discount_invalid` when it doesn't apply, so the price never surprises. */
    @field:Size(max = 60, message = "Deze kortingscode is te lang.")
    val discountCode: String? = null,
)

data class CheckoutStartDto(
    val orderNumber: String,
    val accessToken: String,
    /** The payment page at Stripe. */
    val redirectUrl: String,
)
