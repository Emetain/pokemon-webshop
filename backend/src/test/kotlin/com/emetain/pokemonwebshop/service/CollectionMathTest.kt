package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.repository.OwnedLine
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import kotlin.test.assertEquals

class CollectionMathTest {

    private val rates = ExchangeRates(mapOf(Currency.USD to BigDecimal("1.25")))

    private fun euro(cents: Long?, quantity: Int = 1, cardId: Long = 1) =
        OwnedLine(cardId, Game.POKEMON, 1, "normal", quantity, cents, cents?.let { Currency.EUR })

    private fun dollar(cents: Long, quantity: Int = 1, cardId: Long = 2) =
        OwnedLine(cardId, Game.LORCANA, 2, "normal", quantity, cents, Currency.USD)

    private fun value(vararg lines: OwnedLine, currency: Currency = Currency.EUR, using: ExchangeRates = rates) =
        CollectionValueCalculator.value(lines.toList(), currency, using)

    @ParameterizedTest
    @CsvSource(
        "0, 100, 0.0",
        "1, 3, 33.3",
        "2, 3, 66.6",
        "50, 100, 50.0",
        "100, 100, 100.0",
        // Never 100% before it's really complete.
        "999, 1000, 99.9",
        "9999, 10000, 99.9",
        // A single card of all of Magic still has to look like a start and not like an error.
        "1, 110861, 0.0",
    )
    fun `percent rounds down to one decimal`(owned: Long, total: Long, percent: Double) {
        assertEquals(percent, Progress(owned, total).percent)
    }

    @Test
    fun `an empty set counts as zero percent`() {
        assertEquals(0.0, Progress(0, 0).percent)
    }

    @Test
    fun `owning more than the total stops at 100 percent`() {
        assertEquals(100.0, Progress(12, 10).percent)
    }

    @Test
    fun `progress adds up per part, not per percentage`() {
        assertEquals(Progress(3, 110), Progress(1, 10) + Progress(2, 100))
    }

    @Test
    fun `value multiplies the trend price by the quantity`() {
        assertEquals(CollectionValue(3500, Currency.EUR, 0), value(euro(1000, quantity = 3), euro(500)))
    }

    @Test
    fun `an empty collection is worth nothing`() {
        assertEquals(CollectionValue(0, Currency.EUR, 0), value())
    }

    @Test
    fun `dollar prices are converted to euros`() {
        assertEquals(CollectionValue(1800, Currency.EUR, 0), value(euro(1000), dollar(1000)))
    }

    @Test
    fun `euro prices are converted to dollars for a customer who chose dollars`() {
        val result = value(euro(1000), dollar(1000), currency = Currency.USD)

        assertEquals(CollectionValue(2250, Currency.USD, 0), result)
    }

    @Test
    fun `rounding happens once at the end and not per card`() {
        // Each card is 0.8 cents. Rounded per card that would be 1 cent each, so 3 instead of the real 2.4.
        val cards = Array(3) { dollar(1, cardId = it.toLong()) }

        assertEquals(2, value(*cards).cents)
    }

    @Test
    fun `cards without a price are counted per copy`() {
        assertEquals(CollectionValue(1000, Currency.EUR, 3), value(euro(1000), euro(null, quantity = 3)))
    }

    @Test
    fun `prices that can't be converted count as cards without a price`() {
        val result = value(euro(1000), dollar(500, quantity = 2), using = ExchangeRates(emptyMap()))

        assertEquals(CollectionValue(1000, Currency.EUR, 2), result)
    }

    @Test
    fun `a large collection stays exact`() {
        val result = value(euro(999_999_999, quantity = 9999))

        assertEquals(9_998_999_990_001, result.cents)
    }

    @Test
    fun `the most valuable cards compare across currencies`() {
        val lines = listOf(euro(900, cardId = 1), dollar(1000, cardId = 2), euro(700, cardId = 3))

        // $10 is €8, so the euro card of €9 goes first.
        val cardIds = CollectionValueCalculator.mostValuable(lines, rates, limit = 2).map { it.cardId }

        assertEquals(listOf(1L, 2L), cardIds)
    }

    @Test
    fun `the most valuable cards leave out cards without a price and list a card once`() {
        val reverse = euro(5000, cardId = 1).copy(variant = "reverse")
        val lines = listOf(euro(100, cardId = 1), reverse, euro(null, cardId = 2))

        val result = CollectionValueCalculator.mostValuable(lines, rates, limit = 10)

        assertEquals(listOf(reverse), result)
    }
}
