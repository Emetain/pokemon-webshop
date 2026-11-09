package com.emetain.pokemonwebshop.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrderMathTest {

    private val nl = ShippingPrice(priceCents = 495, freeFromCents = 7500)

    @Test
    fun `the total is the lines plus shipping`() {
        val totals = OrderMath.totals(listOf(PricedLine(1295, 2), PricedLine(495, 3)), nl)

        assertEquals(4075, totals.subtotalCents)
        assertEquals(495, totals.shippingCents)
        assertEquals(4570, totals.totalCents)
        assertEquals(3425, totals.remainingForFreeShippingCents)
    }

    @Test
    fun `shipping is free from the threshold on, exactly at it too`() {
        val atThreshold = OrderMath.totals(listOf(PricedLine(2500, 3)), nl)
        assertEquals(0, atThreshold.shippingCents)
        assertEquals(7500, atThreshold.totalCents)
        assertNull(atThreshold.remainingForFreeShippingCents)

        val justBelow = OrderMath.totals(listOf(PricedLine(7499, 1)), nl)
        assertEquals(495, justBelow.shippingCents)
        assertEquals(1, justBelow.remainingForFreeShippingCents)
    }

    @Test
    fun `a rate without a threshold is never free`() {
        val totals = OrderMath.totals(listOf(PricedLine(50_000, 1)), ShippingPrice(895, null))

        assertEquals(895, totals.shippingCents)
        assertNull(totals.remainingForFreeShippingCents)
    }

    @Test
    fun `an empty cart costs nothing, also no shipping`() {
        val totals = OrderMath.totals(emptyList(), nl)

        assertEquals(0, totals.totalCents)
        assertEquals(0, totals.shippingCents)
        assertNull(totals.remainingForFreeShippingCents)
    }

    @Test
    fun `without a shipping rate the total is the subtotal`() {
        assertEquals(1000, OrderMath.totals(listOf(PricedLine(1000, 1)), null).totalCents)
    }

    @Test
    fun `the vat is 21 of every 121 cents, rounded half up`() {
        assertEquals(21, OrderMath.vatIncluded(121))
        assertEquals(0, OrderMath.vatIncluded(0))
        // 1000 * 21 / 121 = 173.55
        assertEquals(174, OrderMath.vatIncluded(1000))
        // 495 * 21 / 121 = 85.909
        assertEquals(86, OrderMath.vatIncluded(495))
        // 2 * 21 / 121 = 0.347
        assertEquals(0, OrderMath.vatIncluded(2))
    }

    @Test
    fun `the vat is taken over the total including shipping`() {
        val totals = OrderMath.totals(listOf(PricedLine(1000, 1)), nl)

        assertEquals(1495, totals.totalCents)
        assertEquals(OrderMath.vatIncluded(1495), totals.vatCents)
    }

    @Test
    fun `big orders stay exact in cents`() {
        val totals = OrderMath.totals(listOf(PricedLine(99_999_99, 99)), nl)

        assertEquals(989_999_901, totals.subtotalCents)
        assertEquals(989_999_901, totals.totalCents)
    }

    @Test
    fun `a line without a quantity is refused`() {
        assertThrows<IllegalArgumentException> { OrderMath.totals(listOf(PricedLine(100, 0)), nl) }
    }
}
