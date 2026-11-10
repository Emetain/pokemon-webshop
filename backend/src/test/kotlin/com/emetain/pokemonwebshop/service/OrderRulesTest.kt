package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Carrier
import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.ShipmentPreference
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderRulesTest {

    @Test
    fun `paid only comes from the webhook, never by hand`() {
        OrderStatus.entries.forEach { from ->
            assertFalse(OrderStatus.PAID in OrderRules.manualTargets(from) && from != OrderStatus.PROCESSING, "from $from")
            assertFalse(OrderStatus.CANCELLED in OrderRules.manualTargets(from), "cancel has its own action, from $from")
            assertFalse(OrderStatus.REFUNDED in OrderRules.manualTargets(from), "refund has its own action, from $from")
        }
        assertEquals(emptySet(), OrderRules.manualTargets(OrderStatus.PENDING_PAYMENT))
    }

    @Test
    fun `the admin moves a paid order on and can step back once`() {
        assertEquals(setOf(OrderStatus.PROCESSING), OrderRules.manualTargets(OrderStatus.PAID))
        assertEquals(setOf(OrderStatus.PAID), OrderRules.manualTargets(OrderStatus.PROCESSING))
        assertEquals(setOf(OrderStatus.DELIVERED), OrderRules.manualTargets(OrderStatus.SHIPPED))
        assertEquals(setOf(OrderStatus.SHIPPED), OrderRules.manualTargets(OrderStatus.DELIVERED))
        assertEquals(emptySet(), OrderRules.manualTargets(OrderStatus.CANCELLED))
        assertEquals(emptySet(), OrderRules.manualTargets(OrderStatus.REFUNDED))
    }

    @Test
    fun `cancelling is for orders that haven't left the shop`() {
        assertTrue(OrderRules.canCancel(OrderStatus.PENDING_PAYMENT))
        assertTrue(OrderRules.canCancel(OrderStatus.PAID))
        assertTrue(OrderRules.canCancel(OrderStatus.PROCESSING))
        assertFalse(OrderRules.canCancel(OrderStatus.SHIPPED))
        assertFalse(OrderRules.canCancel(OrderStatus.DELIVERED))
        assertFalse(OrderRules.canCancel(OrderStatus.CANCELLED))
    }

    @Test
    fun `shipping needs a paid order, and a shipped one can get a corrected code`() {
        assertFalse(OrderRules.canShip(OrderStatus.PENDING_PAYMENT))
        assertTrue(OrderRules.canShip(OrderStatus.PAID))
        assertTrue(OrderRules.canShip(OrderStatus.PROCESSING))
        assertTrue(OrderRules.canShip(OrderStatus.SHIPPED))
        assertFalse(OrderRules.canShip(OrderStatus.CANCELLED))
    }

    @Test
    fun `a refund needs money left to pay back`() {
        val shipped = order(OrderStatus.SHIPPED, paid = true)
        assertTrue(OrderRules.canRefund(shipped))
        shipped.refundedCents = shipped.totalCents
        assertFalse(OrderRules.canRefund(shipped))

        assertFalse(OrderRules.canRefund(order(OrderStatus.SHIPPED, paid = false)))
        assertFalse(OrderRules.canRefund(order(OrderStatus.PAID, paid = true)), "a paid order is cancelled, not refunded")
        assertTrue(OrderRules.canRefund(order(OrderStatus.CANCELLED, paid = true)), "an automatic refund that failed")
    }

    @Test
    fun `order numbers are short, random and without letters that look alike`() {
        val numbers = (1..500).map { OrderNumbers.generate() }
        numbers.forEach {
            assertTrue(Regex("PT-[2-9A-HJKMNP-Z]{6}").matches(it), it)
        }
        assertTrue(numbers.toSet().size > 495)
        assertEquals("PT-7K3M9Q", OrderNumbers.normalize("  pt-7k3m9q "))
    }

    @Test
    fun `the PostNL link carries the code, the country and the postcode without a space`() {
        val link = TrackingLinks.build("https://jouw.postnl.nl/track-and-trace/{code}-{country}-{postcode}", " 3SABC123 ", "1421 AB", "NL")
        assertEquals("https://jouw.postnl.nl/track-and-trace/3SABC123-NL-1421AB", link)
    }

    @Test
    fun `a code can't break out of the link`() {
        val link = TrackingLinks.build("https://example.com/t/{code}", "a b/../?x=1", "1000", "BE")
        assertEquals("https://example.com/t/a%20b%2F..%2F%3Fx%3D1", link)
        assertNull(TrackingLinks.build(null, "123", "1000", "BE"))
    }

    @Test
    fun `only https links go to customers`() {
        assertTrue(TrackingLinks.isSafe("https://www.dhl.com/track?id=1"))
        assertFalse(TrackingLinks.isSafe("http://www.dhl.com/track?id=1"))
        assertFalse(TrackingLinks.isSafe("javascript:alert(1)"))
        assertFalse(TrackingLinks.isSafe("https://"))
        assertEquals("PostNL", TrackingLinks.carrierLabel(Carrier.POSTNL))
    }

    @Test
    fun `payment methods follow the country`() {
        assertEquals(listOf("ideal", "card"), PaymentMethods.forCountry("NL"))
        assertEquals(listOf("bancontact", "card"), PaymentMethods.forCountry("BE"))
    }

    @Test
    fun `euro amounts read the Dutch way`() {
        assertEquals("€ 12,95", Money.format(1295))
        assertEquals("€ 0,05", Money.format(5))
        assertEquals("€ 1.234,50", Money.format(123450))
    }

    private fun order(status: OrderStatus, paid: Boolean) = Order(
        number = "PT-TEST23",
        user = null,
        email = "a@example.com",
        customerName = "Ash",
        street = "Straat",
        houseNumber = "1",
        postcode = "1421 AB",
        city = "Uithoorn",
        country = "NL",
        subtotalCents = 1000,
        shippingCents = 495,
        totalCents = 1495,
        vatCents = 259,
        deliveryTime = null,
        shipmentPreference = ShipmentPreference.TOGETHER,
        accessToken = "token",
        cartId = null,
        termsAcceptedAt = Instant.EPOCH,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        status = status,
        paidAt = if (paid) Instant.EPOCH else null,
    )
}
