package com.emetain.pokemonwebshop.service

import java.math.BigDecimal
import java.math.RoundingMode

/** One line to pay for: what one costs and how many. */
data class PricedLine(val unitPriceCents: Long, val quantity: Int) {
    val totalCents: Long get() = unitPriceCents * quantity
}

/** What shipping to the chosen country costs, as far as the totals are concerned. */
data class ShippingPrice(val priceCents: Long, val freeFromCents: Long?)

data class OrderTotals(
    val subtotalCents: Long,
    val shippingCents: Long,
    val totalCents: Long,
    /** The VAT included in the total. */
    val vatCents: Long,
    /** How much more makes shipping free. Null when it's free already or never is. */
    val remainingForFreeShippingCents: Long?,
)

/** Order totals in euro cents, VAT included. Pure, so every rule can be tested without a database. */
object OrderMath {

    /** 21%, the Dutch standard rate. Every product in the shop has it. */
    private val VAT_RATE = BigDecimal("0.21")

    fun totals(lines: List<PricedLine>, shipping: ShippingPrice?): OrderTotals {
        require(lines.all { it.unitPriceCents >= 0 && it.quantity > 0 }) { "Every line needs a price and a quantity" }
        val subtotal = lines.sumOf { it.totalCents }
        val freeShipping = shipping?.freeFromCents?.let { subtotal >= it } ?: false
        val shippingCents = when {
            shipping == null || lines.isEmpty() || freeShipping -> 0
            else -> shipping.priceCents
        }
        val total = subtotal + shippingCents
        val remaining = shipping?.freeFromCents
            ?.takeIf { !freeShipping && lines.isNotEmpty() }
            ?.let { it - subtotal }
        return OrderTotals(subtotal, shippingCents, total, vatIncluded(total), remaining)
    }

    /** The VAT in an amount that includes it: 21/121 of it, rounded half up to a cent. */
    fun vatIncluded(amountCents: Long): Long =
        BigDecimal.valueOf(amountCents)
            .multiply(VAT_RATE)
            .divide(BigDecimal.ONE + VAT_RATE, 0, RoundingMode.HALF_UP)
            .longValueExact()
}
