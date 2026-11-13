package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.DiscountKind
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.ProductType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Discount codes alone and together with sale prices, drop prices and shipping. Bundles and quantity discounts
 * were left out of phase 8 by the owner, so there is nothing to combine with those.
 */
class DiscountRulesTest {

    private val nl = ShippingPrice(priceCents = 495, freeFromCents = 7500)

    private fun percent(value: Int, vararg games: Game, types: Set<ProductType> = emptySet(), minimum: Long? = null) =
        DiscountTerms(DiscountKind.PERCENTAGE, percent = value, games = games.toSet(), productTypes = types, minimumOrderCents = minimum)

    private fun fixed(cents: Long, vararg games: Game, types: Set<ProductType> = emptySet(), minimum: Long? = null, drops: Boolean = false) =
        DiscountTerms(
            DiscountKind.FIXED_AMOUNT,
            amountCents = cents,
            games = games.toSet(),
            productTypes = types,
            minimumOrderCents = minimum,
            appliesToDrops = drops,
        )

    private fun line(cents: Long, quantity: Int = 1, game: Game? = Game.POKEMON, type: ProductType = ProductType.BOOSTER_BOX) =
        DiscountLine(cents, quantity, game, type)

    /** Discount and totals the way checkout does it. */
    private fun totals(terms: DiscountTerms, lines: List<DiscountLine>, shipping: ShippingPrice? = nl): OrderTotals =
        OrderMath.totals(lines.map { PricedLine(it.unitPriceCents, it.quantity) }, shipping, DiscountRules.discountCents(terms, lines))

    @Test
    fun `a percentage comes off the products, not off shipping`() {
        val totals = totals(percent(10), listOf(line(2000, 2)))

        assertEquals(4000, totals.subtotalCents)
        assertEquals(400, totals.discountCents)
        assertEquals(495, totals.shippingCents)
        assertEquals(4095, totals.totalCents)
    }

    @ParameterizedTest
    @CsvSource(
        // 10% of 4,95 is 49.5 cents: half up.
        "495, 10, 50",
        // 15% of 9,99 is 149.85.
        "999, 15, 150",
        // 33% of 1,00.
        "100, 33, 33",
        "12345, 100, 12345",
    )
    fun `a percentage is rounded half up to a cent`(cents: Long, value: Int, expected: Long) {
        assertEquals(expected, DiscountRules.discountCents(percent(value), listOf(line(cents))))
    }

    @Test
    fun `a fixed amount never goes beyond the products, so shipping is still paid`() {
        val totals = totals(fixed(5000), listOf(line(1500)))

        assertEquals(1500, totals.discountCents)
        assertEquals(495, totals.shippingCents)
        assertEquals(495, totals.totalCents)
    }

    @Test
    fun `free shipping goes by the products after the discount`() {
        // 80 euro of products is over the threshold of 75, but 10% off brings it to 72.
        val discounted = totals(percent(10), listOf(line(8000)))
        assertEquals(7200, discounted.subtotalCents - discounted.discountCents)
        assertEquals(495, discounted.shippingCents)
        assertEquals(300, discounted.remainingForFreeShippingCents)
        assertEquals(7695, discounted.totalCents)

        // 5 euro off 85 stays above it.
        val stillFree = totals(fixed(500), listOf(line(8500)))
        assertEquals(0, stillFree.shippingCents)
        assertEquals(8000, stillFree.totalCents)
        assertNull(stillFree.remainingForFreeShippingCents)
    }

    @Test
    fun `a sale price is the price the discount works from`() {
        val salePrice = ProductRules.shownPriceCents(priceCents = 5000, salePriceCents = 4000)!!
        val totals = totals(percent(25), listOf(line(salePrice)))

        assertEquals(1000, totals.discountCents)
        assertEquals(3000 + 495, totals.totalCents)
    }

    @Test
    fun `only the games of the code count, and a fixed amount stops at what they cost`() {
        val lines = listOf(line(3000, game = Game.POKEMON), line(1200, game = Game.MTG), line(800, game = null, type = ProductType.ACCESSORY))

        assertEquals(300, DiscountRules.discountCents(percent(10, Game.POKEMON), lines))
        assertEquals(3000, DiscountRules.discountCents(fixed(10_000, Game.POKEMON), lines))
        assertEquals(1200, DiscountRules.discountCents(fixed(10_000, Game.MTG, Game.RIFTBOUND), lines))
        // An accessory has no game: a code for some games skips it.
        assertEquals(4200, DiscountRules.eligibleCents(fixed(1, Game.POKEMON, Game.MTG), lines))
    }

    @Test
    fun `only the product types of the code count, together with its games`() {
        val boosterBox = line(10_000, type = ProductType.BOOSTER_BOX)
        val single = line(2500, type = ProductType.SINGLE)
        val lorcanaBox = line(9000, game = Game.LORCANA, type = ProductType.BOOSTER_BOX)
        val lines = listOf(boosterBox, single, lorcanaBox)

        assertEquals(250, DiscountRules.discountCents(percent(10, types = setOf(ProductType.SINGLE)), lines))
        assertEquals(1000, DiscountRules.discountCents(percent(10, Game.POKEMON, types = setOf(ProductType.BOOSTER_BOX)), lines))
        assertEquals(1900, DiscountRules.discountCents(percent(10, types = setOf(ProductType.BOOSTER_BOX)), lines))
    }

    @Test
    fun `the minimum goes by all products before the discount, not by the products the code counts for`() {
        val terms = percent(10, Game.LORCANA, minimum = 5000)

        assertEquals(DiscountProblem.BELOW_MINIMUM, DiscountRules.orderProblem(terms, listOf(line(4999, game = Game.LORCANA)), drop = false))
        assertNull(DiscountRules.orderProblem(terms, listOf(line(5000, game = Game.LORCANA)), drop = false))
        assertNull(DiscountRules.orderProblem(terms, listOf(line(1000, game = Game.LORCANA), line(4000, game = Game.POKEMON)), drop = false))
    }

    @Test
    fun `a code for other products doesn't apply`() {
        val problem = DiscountRules.orderProblem(percent(10, Game.ONE_PIECE), listOf(line(2000)), drop = false)

        assertEquals(DiscountProblem.NO_MATCHING_PRODUCTS, problem)
    }

    @Test
    fun `drop prices only get a discount when the code says so`() {
        val dropLine = listOf(line(4500))

        assertEquals(DiscountProblem.NOT_FOR_DROPS, DiscountRules.orderProblem(fixed(500), dropLine, drop = true))
        assertNull(DiscountRules.orderProblem(fixed(500, drops = true), dropLine, drop = true))
        // In the normal shop the switch makes no difference.
        assertNull(DiscountRules.orderProblem(fixed(500), dropLine, drop = false))
    }

    @Test
    fun `a drop price with a code for drops, then shipping`() {
        val dropPrice = 4500L
        val totals = totals(fixed(1000, drops = true), listOf(line(dropPrice, quantity = 2)))

        assertEquals(9000, totals.subtotalCents)
        assertEquals(1000, totals.discountCents)
        // 80 euro after the discount: still free shipping.
        assertEquals(0, totals.shippingCents)
        assertEquals(8000, totals.totalCents)
    }

    @Test
    fun `the vat is worked out over what is paid after the discount`() {
        val totals = totals(fixed(1000), listOf(line(2210)))

        assertEquals(1210 + 495, totals.totalCents)
        assertEquals(OrderMath.vatIncluded(1705), totals.vatCents)
    }

    @Test
    fun `valid from the start up to the end, and never while switched off`() {
        val start = Instant.parse("2026-11-12T08:00:00Z")
        val end = Instant.parse("2026-11-19T08:00:00Z")

        assertEquals(DiscountProblem.NOT_STARTED, DiscountRules.timeProblem(true, start, end, start.minusSeconds(1)))
        assertNull(DiscountRules.timeProblem(true, start, end, start))
        assertNull(DiscountRules.timeProblem(true, start, end, end.minusSeconds(1)))
        assertEquals(DiscountProblem.EXPIRED, DiscountRules.timeProblem(true, start, end, end))
        assertEquals(DiscountProblem.INACTIVE, DiscountRules.timeProblem(false, null, null, start))
        assertNull(DiscountRules.timeProblem(true, null, null, start))
    }

    @Test
    fun `maximum uses and once per customer`() {
        assertNull(DiscountRules.usageProblem(maxUses = 3, uses = 2, oncePerCustomer = false, usesByCustomer = 0))
        assertEquals(DiscountProblem.USED_UP, DiscountRules.usageProblem(maxUses = 3, uses = 3, oncePerCustomer = false, usesByCustomer = 0))
        assertNull(DiscountRules.usageProblem(maxUses = null, uses = 1000, oncePerCustomer = true, usesByCustomer = 0))
        assertEquals(DiscountProblem.ALREADY_USED, DiscountRules.usageProblem(maxUses = null, uses = 1, oncePerCustomer = true, usesByCustomer = 1))
    }

    @Test
    fun `codes are written one way, and an unusable code doesn't say why`() {
        assertEquals("WELKOM10", DiscountRules.normalize(" welkom 10 "))
        assertEquals(DiscountRules.message(DiscountProblem.UNKNOWN, null), DiscountRules.message(DiscountProblem.EXPIRED, null))
        assertEquals(DiscountRules.message(DiscountProblem.UNKNOWN, null), DiscountRules.message(DiscountProblem.USED_UP, null))
        assertEquals(
            "Deze kortingscode geldt vanaf € 50,00 aan producten.",
            DiscountRules.message(DiscountProblem.BELOW_MINIMUM, percent(10, minimum = 5000)),
        )
    }
}
