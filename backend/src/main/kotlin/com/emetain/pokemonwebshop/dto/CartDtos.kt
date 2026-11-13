package com.emetain.pokemonwebshop.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDate

data class CartItemRequest(
    val productId: Long = 0,

    @field:Min(1, message = "Kies minstens 1 stuk.")
    @field:Max(99, message = "Je kunt maximaal 99 stuks tegelijk bestellen.")
    val quantity: Int = 1,
)

data class CartQuantityRequest(
    @field:Min(0, message = "Kies een aantal van 0 of meer.")
    @field:Max(99, message = "Je kunt maximaal 99 stuks tegelijk bestellen.")
    val quantity: Int = 1,
)

data class CartLineDto(
    val productId: Long,
    val slug: String,
    val name: String,
    val game: String?,
    val type: String,
    val imageUrl: String?,
    val imageIsCard: Boolean,
    val setLogoUrl: String?,
    /** What one costs now, sale price included. Null while the product has no price. */
    val unitPriceCents: Long?,
    /** The normal price, for the struck-through price during a sale. */
    val priceCents: Long?,
    val onSale: Boolean,
    val quantity: Int,
    val lineTotalCents: Long?,
    /** The most this line can go up to: 99, or 0 when it can't be bought. Not the stock: the shop never shows that. */
    val maxQuantity: Int,
    val canBuy: Boolean,
    val preOrder: Boolean,
    val shipsFrom: LocalDate?,
    /** `SOLD_OUT` or `TOO_MANY`: the line has to change before checkout. */
    val problem: String?,
)

data class CartShippingDto(
    val country: String,
    /** What shipping costs for this cart: 0 once the free threshold is reached. */
    val priceCents: Long,
    val ratePriceCents: Long,
    val freeFromCents: Long?,
    val remainingForFreeCents: Long?,
    val deliveryTime: String,
)

data class CartDto(
    val items: List<CartLineDto>,
    val itemCount: Int,
    /** The products, before a discount code. */
    val subtotalCents: Long,
    /** What the discount code takes off. 0 without a code, or when it doesn't apply. */
    val discountCents: Long,
    /** The discount code asked for, with its amount or why it doesn't apply. */
    val discount: CartDiscountDto?,
    /** Null when the shop doesn't ship to the chosen country. */
    val shipping: CartShippingDto?,
    val totalCents: Long,
    val vatCents: Long,
    val canCheckout: Boolean,
    val hasPreOrders: Boolean,
    val hasInStock: Boolean,
    val paymentMethods: List<String>,
)
