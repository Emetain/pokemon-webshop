package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Currency
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class CollectionHistoryMathTest {

    private val today = LocalDate.of(2026, 11, 6)
    private val yesterday = today.minusDays(1)
    private val lastWeek = today.minusDays(7)

    private val rates = ExchangeRates(mapOf(Currency.USD to BigDecimal("1.25")))

    private fun live(cents: Long, currency: Currency = Currency.EUR) = CollectionValue(cents, currency, 0)

    @Test
    fun `a new collection has one point, today`() {
        val points = CollectionHistoryMath.points(emptyList(), today, live(500), rates)

        assertEquals(listOf(HistoryPoint(today, 500)), points)
    }

    @Test
    fun `the last point is always the live value`() {
        val saved = listOf(HistoryPoint(lastWeek, 100), HistoryPoint(yesterday, 200))

        val points = CollectionHistoryMath.points(saved, today, live(300), rates)

        assertEquals(listOf(HistoryPoint(lastWeek, 100), HistoryPoint(yesterday, 200), HistoryPoint(today, 300)), points)
    }

    @Test
    fun `the live value replaces the snapshot of today, so a new card shows right away`() {
        val saved = listOf(HistoryPoint(yesterday, 200), HistoryPoint(today, 250))

        val points = CollectionHistoryMath.points(saved, today, live(999), rates)

        assertEquals(listOf(HistoryPoint(yesterday, 200), HistoryPoint(today, 999)), points)
    }

    @Test
    fun `points are sorted oldest first`() {
        val saved = listOf(HistoryPoint(yesterday, 200), HistoryPoint(lastWeek, 100))

        val dates = CollectionHistoryMath.points(saved, today, live(300), rates).map { it.date }

        assertEquals(listOf(lastWeek, yesterday, today), dates)
    }

    @Test
    fun `saved euros are shown in dollars at today's rate`() {
        val points = CollectionHistoryMath.points(
            listOf(HistoryPoint(yesterday, 1000)),
            today,
            live(2000, Currency.USD),
            rates,
        )

        assertEquals(listOf(HistoryPoint(yesterday, 1250), HistoryPoint(today, 2000)), points)
    }

    @Test
    fun `without a rate the past is left out instead of drawn in the wrong currency`() {
        val points = CollectionHistoryMath.points(
            listOf(HistoryPoint(yesterday, 1000)),
            today,
            live(2000, Currency.USD),
            ExchangeRates(emptyMap()),
        )

        assertEquals(listOf(HistoryPoint(today, 2000)), points)
    }
}
