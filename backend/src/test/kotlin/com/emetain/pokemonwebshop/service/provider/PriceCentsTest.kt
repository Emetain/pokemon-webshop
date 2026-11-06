package com.emetain.pokemonwebshop.service.provider

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PriceCentsTest {

    @ParameterizedTest
    @CsvSource("123.63,12363", "0.01,1", "1,100", "19.9,1990", "0.005,1", "1.005,101", "' 2.50 ',250", "1e2,10000")
    fun `converts euros to cents and rounds half up`(euros: String, cents: Long) {
        assertEquals(cents, PriceCents.parse(euros))
    }

    @Test
    fun `doesn't pick up floating point errors`() {
        // As a Double this is 1.00499999..., which would round down to 100.
        assertEquals(101, PriceCents.parse("1.005"))
        assertEquals(101, PriceCents.parse(json("1.005")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "0.00", "0.004", "-1", "-0.5", "abc", "", " ", "NaN", "1,50", "1e400", "99999999999"])
    fun `anything that isn't a real price means no price`(euros: String) {
        assertNull(PriceCents.parse(euros))
    }

    @Test
    fun `reads numbers and text from json`() {
        assertEquals(12363, PriceCents.parse(json("123.63")))
        assertEquals(64, PriceCents.parse(json("\"0.64\"")))
    }

    @Test
    fun `null and missing json values mean no price`() {
        assertNull(PriceCents.parse(json("null")))
        assertNull(PriceCents.parse(json("{}").path("trend")))
        assertNull(PriceCents.parse(json("{}")))
        assertNull(PriceCents.parse(null as String?))
    }
}
