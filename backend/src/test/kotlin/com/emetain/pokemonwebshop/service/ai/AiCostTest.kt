package com.emetain.pokemonwebshop.service.ai

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiCostTest {

    @Test
    fun `tokens times the price per million, in millionths of a dollar, rounded up`() {
        // gpt-6.1-terra: $1 in and $5 out per million tokens.
        assertEquals(1_200, AiCost.micros(600, 120, BigDecimal("1.00"), BigDecimal("5.00")))
        // An image of 1568 output tokens at $30 per million, plus 80 text tokens at $5.
        assertEquals(47_440, AiCost.micros(80, 1568, BigDecimal("5.00"), BigDecimal("30.00")))
        // Half a millionth rounds up, so the shop never counts less than OpenAI charges.
        assertEquals(1, AiCost.micros(1, 0, BigDecimal("0.5"), BigDecimal.ZERO))
    }

    @Test
    fun `a call only starts when its highest cost still fits in the month`() {
        val limit = AiCost.micros(BigDecimal("5"))

        assertEquals(5_000_000, limit)
        assertTrue(AiCost.fits(spentMicros = 4_750_000, estimateMicros = 250_000, limitMicros = limit))
        assertFalse(AiCost.fits(spentMicros = 4_750_001, estimateMicros = 250_000, limitMicros = limit))
        assertEquals(BigDecimal("4.7500"), AiCost.dollars(4_750_000))
    }
}
