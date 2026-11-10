package com.emetain.pokemonwebshop.service

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DropRulesTest {

    private val now = Instant.parse("2026-11-14T19:00:00Z")
    private val interval = Duration.ofSeconds(10)

    @Test
    fun `a Gmail address with dots or a tag is one person, other providers keep their dots`() {
        assertEquals("ashketchum@gmail.com", DropRules.emailKey(" Ash.Ketchum+drop@Gmail.com "))
        assertEquals("ashketchum@gmail.com", DropRules.emailKey("ashketchum@googlemail.com"))
        assertEquals("ash.ketchum@outlook.com", DropRules.emailKey("Ash.Ketchum+1@outlook.com"))
        assertEquals("geen-adres", DropRules.emailKey("geen-adres"))
    }

    @Test
    fun `a Dutch address is its postcode and house number, written any way`() {
        val key = DropRules.addressKey("Dorpsstraat", "12a", "1421 AB", "NL")
        assertEquals("NL|1421AB|12A", key)
        assertEquals(key, DropRules.addressKey("dorpstraat", "12 - A", "1421ab", "NL"))
        assertNotEquals(key, DropRules.addressKey("Dorpsstraat", "12b", "1421 AB", "NL"))
    }

    @Test
    fun `a Belgian address needs the street too, because a postcode covers a whole town`() {
        assertEquals("BE|1000|RUEDELALOI|16", DropRules.addressKey("Rue de la Loi", "16", "1000", "BE"))
        assertEquals(DropRules.addressKey("Rue de la Loi", "16", "1000", "BE"), DropRules.addressKey("rue de la lói", "16", "1000", "BE"))
        assertNotEquals(DropRules.addressKey("Rue de la Loi", "16", "1000", "BE"), DropRules.addressKey("Wetstraat", "16", "1000", "BE"))
    }

    @Test
    fun `60 a minute lets 10 through every 10 seconds`() {
        val batch = DropRules.admissionBatch(60, now.minus(interval), now, interval)
        assertEquals(10, batch.size)
        assertEquals(now, batch.usedUntil)
    }

    @Test
    fun `a slow speed keeps its exact rate over several batches`() {
        // 5 a minute is one every 12 seconds: nobody after 10 seconds, then one per batch, five in the minute.
        var usedUntil: Instant = now
        val sizes = (1..6).map { step ->
            val batch = DropRules.admissionBatch(5, usedUntil, now.plus(interval.multipliedBy(step.toLong())), interval)
            usedUntil = batch.usedUntil
            batch.size
        }
        assertEquals(listOf(0, 1, 1, 1, 1, 1), sizes)
        assertEquals(now.plusSeconds(60), usedUntil, "the rate is used up to exactly one minute")
    }

    @Test
    fun `after a pause at most two batches catch up`() {
        val batch = DropRules.admissionBatch(60, now.minus(Duration.ofMinutes(10)), now, interval)
        assertEquals(20, batch.size)
    }

    @Test
    fun `the estimated wait never says sooner than the next batch`() {
        assertEquals(10, DropRules.estimatedWaitSeconds(0, 60, interval))
        assertEquals(60, DropRules.estimatedWaitSeconds(59, 60, interval))
        assertEquals(300, DropRules.estimatedWaitSeconds(49, 10, interval))
        assertNull(DropRules.estimatedWaitSeconds(5, 0, interval))
    }

    @Test
    fun `limits per product and per drop count what was ordered`() {
        fun check(maxProduct: Int?, maxDrop: Int?, orderedProduct: Int, orderedDrop: Int, wantedProduct: Int, wantedDrop: Int) =
            DropRules.limitProblem(DropLimitCheck(maxProduct, maxDrop, orderedProduct, orderedDrop, wantedProduct, wantedDrop))

        assertNull(check(2, 3, 0, 0, 2, 2))
        assertEquals(DropLimitProblem.PRODUCT, check(2, 3, 1, 1, 2, 2))
        assertEquals(DropLimitProblem.DROP, check(2, 3, 0, 2, 1, 2))
        assertNull(check(null, null, 50, 50, 99, 99))
    }

    @Test
    fun `room is the most a line can go up to`() {
        assertEquals(2, DropRules.roomLeft(2, null, 0, 0, 0))
        assertEquals(1, DropRules.roomLeft(2, 3, 0, 1, 1))
        assertEquals(0, DropRules.roomLeft(1, null, 1, 1, 0))
        assertNull(DropRules.roomLeft(null, null, 4, 4, 4))
    }

    @Test
    fun `the shuffle keeps everyone and doesn't follow the order people joined`() {
        val waiting = (1..1000).toList()
        val shuffled = DropRules.shuffle(waiting)
        assertEquals(waiting.toSet(), shuffled.toSet())
        assertNotEquals(waiting, shuffled)
        // Everyone has an equal chance to be first, also whoever joined first.
        val firsts = (1..4000).map { DropRules.shuffle(listOf(1, 2, 3, 4), Random(it))[0] }.groupingBy { it }.eachCount()
        assertTrue(firsts.values.all { it in 850..1150 }, "first places: $firsts")
    }
}
