package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Currency
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExchangeRatesTest {

    private val rates = ExchangeRates(mapOf(Currency.USD to BigDecimal("1.25")))

    @Test
    fun `dollars become euros by dividing by the rate`() {
        assertEquals(800, rates.convertCents(1000, Currency.USD, Currency.EUR))
    }

    @Test
    fun `euros become dollars by multiplying by the rate`() {
        assertEquals(1250, rates.convertCents(1000, Currency.EUR, Currency.USD))
    }

    @Test
    fun `the same currency stays as it is, even without any rates`() {
        val noRates = ExchangeRates(emptyMap())

        assertEquals(1234, noRates.convertCents(1234, Currency.EUR, Currency.EUR))
        assertEquals(1234, noRates.convertCents(1234, Currency.USD, Currency.USD))
    }

    @Test
    fun `without a rate there is no answer, instead of a wrong one`() {
        val noRates = ExchangeRates(emptyMap())

        assertNull(noRates.convertCents(1000, Currency.USD, Currency.EUR))
        assertNull(noRates.convertCents(1000, Currency.EUR, Currency.USD))
    }

    @Test
    fun `half a cent rounds up`() {
        assertEquals(1, ExchangeRates(mapOf(Currency.USD to BigDecimal("2"))).convertCents(1, Currency.USD, Currency.EUR))
    }
}
