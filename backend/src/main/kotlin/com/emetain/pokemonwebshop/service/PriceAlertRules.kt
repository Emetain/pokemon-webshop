package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceAlert
import com.emetain.pokemonwebshop.domain.PriceAlertKind
import java.time.LocalDate

/** A card's price today, as the card page shows it. */
data class Price(val cents: Long, val currency: Currency)

/** Why an alert went off, for the mail: the price now and what it was compared with, in the alert's own currency. */
data class AlertHit(
    val alert: PriceAlert,
    val nowCents: Long,
    val currency: Currency,
    /** [PriceAlertKind.BELOW]: the line. [PriceAlertKind.CHANGE]: the price it moved from. */
    val comparedCents: Long,
    /** [PriceAlertKind.CHANGE]: how far it moved, rounded down to whole percents, with its sign. */
    val percent: Int? = null,
)

/**
 * When a price alert goes off (phase 12). Pure: the daily check feeds it today's price and saves what it changed.
 *
 * - **Below a price:** goes off the first day the price is under the line, then waits until the price is back at or
 *   above it before it can go off again. A price that stays low sends one mail, not one every day.
 * - **Up or down by a percentage:** measured from the price when the alert was set. When it goes off, that day's price
 *   becomes the new starting point, so the next mail comes after the next move of that size.
 *
 * A card without a price, or a price without an exchange rate to compare it in, changes nothing.
 */
object PriceAlertRules {

    const val MIN_PERCENT = 1
    const val MAX_PERCENT = 1000

    fun check(alert: PriceAlert, price: Price?, rates: ExchangeRates, today: LocalDate): AlertHit? {
        if (price == null) return null
        return when (alert.kind) {
            PriceAlertKind.BELOW -> below(alert, price, rates, today)
            PriceAlertKind.CHANGE -> change(alert, price, rates, today)
        }
    }

    /** What a new alert starts from: a percentage alert remembers today's price. */
    fun start(alert: PriceAlert, price: Price?) {
        if (alert.kind == PriceAlertKind.CHANGE && price != null) {
            alert.baselineCents = price.cents
            alert.baselineCurrency = price.currency
        }
        alert.armed = true
    }

    private fun below(alert: PriceAlert, price: Price, rates: ExchangeRates, today: LocalDate): AlertHit? {
        val line = alert.thresholdCents ?: return null
        val currency = alert.currency ?: return null
        val now = rates.convertCents(price.cents, price.currency, currency) ?: return null
        if (now >= line) {
            alert.armed = true
            return null
        }
        if (!alert.armed) return null
        alert.armed = false
        alert.lastTriggeredOn = today
        return AlertHit(alert, now, currency, line)
    }

    private fun change(alert: PriceAlert, price: Price, rates: ExchangeRates, today: LocalDate): AlertHit? {
        val percent = alert.percent ?: return null
        val baseline = alert.baselineCents
        val baselineCurrency = alert.baselineCurrency
        if (baseline == null || baselineCurrency == null || baseline <= 0) {
            // Set while the card had no price: today's is where it starts.
            start(alert, price)
            return null
        }
        val then = rates.convertCents(baseline, baselineCurrency, price.currency) ?: return null
        if (then <= 0) return null
        val moved = (price.cents - then) * 100.0 / then
        if (kotlin.math.abs(moved) < percent) return null
        alert.baselineCents = price.cents
        alert.baselineCurrency = price.currency
        alert.lastTriggeredOn = today
        return AlertHit(alert, price.cents, price.currency, then, moved.toInt())
    }
}
