package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceSource
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 12: the price graph per card and the risers and fallers among owned cards. */
class PriceTrendMathTest {

    private val rates = ExchangeRates(mapOf(Currency.USD to BigDecimal("1.25")))
    private val today = LocalDate.of(2026, 11, 25)

    private fun saved(date: LocalDate, cents: Long?, currency: Currency = Currency.EUR) =
        SavedPrice("normal", PriceSource.CARDMARKET, currency, date, cents)

    @Test
    fun `a price saved only on the days it changed becomes one point per day`() {
        val rows = listOf(saved(today.minusDays(4), 100), saved(today.minusDays(2), 150))

        val points = PriceTrendMath.daily(rows, today, liveCents = 150, to = Currency.EUR, rates = rates)

        assertEquals(listOf(100L, 100L, 150L, 150L, 150L), points.map { it.cents })
        assertEquals(today.minusDays(4), points.first().date)
        assertEquals(today, points.last().date)
    }

    @Test
    fun `today is the price the card page shows, and a day without a trend keeps the last one`() {
        val rows = listOf(saved(today.minusDays(2), 100), saved(today.minusDays(1), null))

        val points = PriceTrendMath.daily(rows, today, liveCents = 130, to = Currency.EUR, rates = rates)

        assertEquals(listOf(100L, 100L, 130L), points.map { it.cents })
    }

    @Test
    fun `dollar prices are shown in euros at today's rate`() {
        val points = PriceTrendMath.daily(listOf(saved(today, 1000, Currency.USD)), today, null, Currency.EUR, rates)

        assertEquals(listOf(HistoryPoint(today, 800)), points)
    }

    @Test
    fun `nothing saved is no graph`() {
        assertTrue(PriceTrendMath.daily(emptyList(), today, 100, Currency.EUR, rates).isEmpty())
    }

    private fun line(cardId: Long, then: Long?, now: Long, currency: Currency = Currency.EUR, thenDate: LocalDate? = today.minusDays(7)) =
        MoverLine(cardId, "normal", 1, currency, now, then, thenDate.takeIf { then != null })

    @Test
    fun `risers and fallers are sorted by how much a copy gained or lost, not by percentage`() {
        val cutoff = today.minusDays(7)
        val lines = listOf(
            line(1, then = 2, now = 10), // +400%, but 8 cents
            line(2, then = 100_00, now = 140_00), // +40%, €40
            line(3, then = 50_00, now = 45_00), // -10%, €5
            line(4, then = 10_00, now = 2_00), // -80%, €8
            line(5, then = 5_00, now = 5_00), // didn't move
        )

        val movers = PriceTrendMath.movers(lines, cutoff, Currency.EUR, rates, limit = 5)

        assertEquals(listOf(2L, 1L), movers.risers.map { it.line.cardId })
        assertEquals(listOf(4L, 3L), movers.fallers.map { it.line.cardId })
        assertEquals(40.0, movers.risers.first().percent)
        assertEquals(-80.0, movers.fallers.first().percent)
        assertEquals(cutoff, movers.since)
    }

    @Test
    fun `dollar cards compete in euros, and are shown in the customer's currency`() {
        val lines = listOf(line(1, then = 10_00, now = 30_00, currency = Currency.USD), line(2, then = 10_00, now = 27_00))

        val movers = PriceTrendMath.movers(lines, today.minusDays(7), Currency.EUR, rates, limit = 5)

        // $20 is €16, less than €17.
        assertEquals(listOf(2L, 1L), movers.risers.map { it.line.cardId })
        assertEquals(16_00, movers.risers[1].changeCents)
    }

    @Test
    fun `when the history began after the start of the period, it says since when`() {
        val began = today.minusDays(2)
        val lines = listOf(line(1, then = 100, now = 200, thenDate = began))

        val movers = PriceTrendMath.movers(lines, today.minusMonths(1), Currency.EUR, rates, limit = 5)

        assertEquals(began, movers.since)
    }

    @Test
    fun `cards without a saved price aren't compared, and the limit holds`() {
        val lines = (1L..8L).map { line(it, then = 100, now = 100 + it) } + line(9, then = null, now = 500)

        val movers = PriceTrendMath.movers(lines, today.minusDays(1), Currency.EUR, rates, limit = 3)

        assertEquals(listOf(8L, 7L, 6L), movers.risers.map { it.line.cardId })
        assertTrue(movers.fallers.isEmpty())
        assertNull(PriceTrendMath.movers(emptyList(), today, Currency.EUR, rates, 3).since)
    }
}
