package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.repository.OwnedLine
import java.math.BigDecimal
import java.math.RoundingMode

/** How far a customer is. [percent] rounds down, so 100 means complete and never "almost". */
data class Progress(val owned: Long, val total: Long) {
    val percent: Double
        get() = if (total == 0L) 0.0 else (owned.coerceAtMost(total) * PER_MILLE / total) / 10.0

    operator fun plus(other: Progress) = Progress(owned + other.owned, total + other.total)

    private companion object {
        const val PER_MILLE = 1000L
    }
}

/** [unpricedCards] are copies that aren't in [cents], because they have no price we can use. */
data class CollectionValue(val cents: Long, val currency: Currency, val unpricedCards: Long)

object CollectionValueCalculator {

    /** Adds up per currency first, so a dollar total is converted and rounded once instead of per card. */
    fun value(lines: List<OwnedLine>, currency: Currency, rates: ExchangeRates): CollectionValue {
        val totals = lines.filter { it.isPriced() }
            .groupBy { it.currency!! }
            .mapValues { (_, priced) -> priced.sumOf { it.quantity.toBigDecimal() * it.trendCents!!.toBigDecimal() } }

        val converted = totals.mapValues { (from, total) -> rates.convert(total, from, currency) }
        val cents = converted.values.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add)
        val noRate = converted.filterValues { it == null }.keys
        val unpriced = lines.filter { !it.isPriced() || it.currency in noRate }.sumOf { it.quantity.toLong() }

        return CollectionValue(cents.setScale(0, RoundingMode.HALF_UP).toLong(), currency, unpriced)
    }

    /** The lines worth the most per copy, whatever currency their price is in. */
    fun mostValuable(lines: List<OwnedLine>, rates: ExchangeRates, limit: Int): List<OwnedLine> =
        lines.filter { it.isPriced() }
            .mapNotNull { line ->
                rates.convertCents(line.trendCents!!, line.currency!!, Currency.EUR)?.let { euroCents -> line to euroCents }
            }
            .sortedWith(compareByDescending<Pair<OwnedLine, Long>> { it.second }.thenBy { it.first.cardId })
            .map { it.first }
            .distinctBy { it.cardId }
            .take(limit)

    private fun OwnedLine.isPriced() = trendCents != null && currency != null
}
