package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Currency
import java.math.BigDecimal
import java.math.RoundingMode

/** Converts through the euro, because the ECB only publishes rates against it. */
class ExchangeRates(perEuro: Map<Currency, BigDecimal>) {

    private val perEuro = perEuro + (Currency.EUR to BigDecimal.ONE)

    /** Null when a rate is missing: an amount that can't be converted must not count as if it was. */
    fun convert(amount: BigDecimal, from: Currency, to: Currency): BigDecimal? {
        if (from == to) return amount
        val fromRate = perEuro[from] ?: return null
        val toRate = perEuro[to] ?: return null
        return amount.multiply(toRate).divide(fromRate, SCALE, RoundingMode.HALF_UP)
    }

    fun convertCents(cents: Long, from: Currency, to: Currency): Long? =
        convert(cents.toBigDecimal(), from, to)?.setScale(0, RoundingMode.HALF_UP)?.toLong()

    private companion object {
        // Keeps a sum of many converted amounts exact to the cent. Rounding happens once, at the very end.
        const val SCALE = 10
    }
}
