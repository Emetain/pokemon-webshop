package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceAlert
import com.emetain.pokemonwebshop.domain.PriceAlertKind
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 12: when a price alert goes off, and that a price that stays put doesn't mail every day. */
class PriceAlertRulesTest {

    private val rates = ExchangeRates(mapOf(Currency.USD to BigDecimal("1.25")))
    private val monday = LocalDate.of(2026, 11, 23)
    private val tuesday = monday.plusDays(1)
    private val wednesday = monday.plusDays(2)
    private val thursday = monday.plusDays(3)

    private fun below(cents: Long, currency: Currency = Currency.EUR) = PriceAlert(
        user = mock(),
        card = mock(),
        variant = "normal",
        kind = PriceAlertKind.BELOW,
        thresholdCents = cents,
        currency = currency,
        createdAt = Instant.EPOCH,
    )

    private fun change(percent: Int, startCents: Long?, currency: Currency = Currency.EUR) = PriceAlert(
        user = mock(),
        card = mock(),
        variant = "normal",
        kind = PriceAlertKind.CHANGE,
        percent = percent,
        createdAt = Instant.EPOCH,
    ).also { PriceAlertRules.start(it, startCents?.let { cents -> Price(cents, currency) }) }

    private fun euros(cents: Long) = Price(cents, Currency.EUR)

    @Test
    fun `below a price goes off the first day the price is under the line`() {
        val alert = below(10_00)

        assertNull(PriceAlertRules.check(alert, euros(12_00), rates, monday))
        val hit = assertNotNull(PriceAlertRules.check(alert, euros(9_50), rates, tuesday))

        assertEquals(9_50, hit.nowCents)
        assertEquals(10_00, hit.comparedCents)
        assertEquals(tuesday, alert.lastTriggeredOn)
    }

    @Test
    fun `exactly on the line isn't below it`() {
        assertNull(PriceAlertRules.check(below(10_00), euros(10_00), rates, monday))
    }

    @Test
    fun `a price that stays low mails once, and again only after it was back above the line`() {
        val alert = below(10_00)

        assertNotNull(PriceAlertRules.check(alert, euros(9_00), rates, monday))
        assertNull(PriceAlertRules.check(alert, euros(8_00), rates, tuesday))
        assertFalse(alert.armed)

        assertNull(PriceAlertRules.check(alert, euros(10_50), rates, wednesday))
        assertTrue(alert.armed)
        assertNotNull(PriceAlertRules.check(alert, euros(9_99), rates, thursday))
    }

    @Test
    fun `the line is in the customer's currency and the price is converted to it`() {
        // A Lorcana card priced in dollars, for a customer who set €10: $12.00 is €9.60.
        val hit = assertNotNull(PriceAlertRules.check(below(10_00), Price(12_00, Currency.USD), rates, monday))

        assertEquals(9_60, hit.nowCents)
        assertEquals(Currency.EUR, hit.currency)
    }

    @Test
    fun `without a rate or a price nothing happens`() {
        val alert = below(10_00)

        assertNull(PriceAlertRules.check(alert, Price(1_00, Currency.USD), ExchangeRates(emptyMap()), monday))
        assertNull(PriceAlertRules.check(alert, null, rates, monday))
        assertTrue(alert.armed)
        assertNull(alert.lastTriggeredOn)
    }

    @Test
    fun `a percentage alert goes off when the price rises that much from where it started`() {
        val alert = change(20, 50_00)

        assertNull(PriceAlertRules.check(alert, euros(59_99), rates, monday))
        val hit = assertNotNull(PriceAlertRules.check(alert, euros(60_00), rates, tuesday))

        assertEquals(20, hit.percent)
        assertEquals(50_00, hit.comparedCents)
        assertEquals(60_00, hit.nowCents)
    }

    @Test
    fun `and when it falls that much`() {
        val hit = assertNotNull(PriceAlertRules.check(change(10, 50_00), euros(44_00), rates, monday))

        assertEquals(-12, hit.percent)
    }

    @Test
    fun `after going off it measures from the new price, so the next mail needs the next move`() {
        val alert = change(10, 100_00)

        assertNotNull(PriceAlertRules.check(alert, euros(111_00), rates, monday))
        assertEquals(111_00, alert.baselineCents)
        // 115 is 3.6% above 111: no new mail, although it's 15% above where it started.
        assertNull(PriceAlertRules.check(alert, euros(115_00), rates, tuesday))
        assertNotNull(PriceAlertRules.check(alert, euros(99_00), rates, wednesday))
    }

    @Test
    fun `a percentage alert set while the card had no price starts at the first price it gets`() {
        val alert = change(10, null)

        assertNull(PriceAlertRules.check(alert, euros(20_00), rates, monday))
        assertEquals(20_00, alert.baselineCents)
        assertNotNull(PriceAlertRules.check(alert, euros(22_00), rates, tuesday))
    }

    @Test
    fun `a card that moved from euros to dollars is compared in one currency`() {
        // Started at €40.00; now $56.00, which is €44.80 at 1.25: 12% up, so a 10% alert goes off.
        val alert = change(10, 40_00)

        val hit = assertNotNull(PriceAlertRules.check(alert, Price(56_00, Currency.USD), rates, monday))

        assertEquals(12, hit.percent)
        assertEquals(Currency.USD, alert.baselineCurrency)
    }
}
