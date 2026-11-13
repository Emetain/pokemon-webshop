package com.emetain.pokemonwebshop.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MysteryBoxMathTest {

    @Test
    fun `every box that is left has the same chance`() {
        // 1 big hit, 9 ETBs, 90 packs: rolls 0..99 map onto them in that order.
        val remaining = listOf(1, 9, 90)

        assertEquals(0, MysteryBoxMath.pick(remaining, 0))
        assertEquals(1, MysteryBoxMath.pick(remaining, 1))
        assertEquals(1, MysteryBoxMath.pick(remaining, 9))
        assertEquals(2, MysteryBoxMath.pick(remaining, 10))
        assertEquals(2, MysteryBoxMath.pick(remaining, 99))
        val counts = (0 until 100).groupingBy { MysteryBoxMath.pick(remaining, it) }.eachCount()
        assertEquals(mapOf(0 to 1, 1 to 9, 2 to 90), counts)
    }

    @Test
    fun `a kind that is gone can't be drawn`() {
        val remaining = listOf(0, 2, 0, 1)

        assertEquals(setOf(1, 3), (0 until 3).map { MysteryBoxMath.pick(remaining, it) }.toSet())
        assertThrows<IllegalArgumentException> { MysteryBoxMath.pick(remaining, 3) }
        assertThrows<IllegalArgumentException> { MysteryBoxMath.pick(listOf(0, 0), 0) }
    }

    @Test
    fun `chances are of the boxes left, in percent with one decimal`() {
        assertEquals(listOf(1.0, 9.0, 90.0), MysteryBoxMath.chances(listOf(1, 9, 90)))
        assertEquals(listOf(33.3, 66.7), MysteryBoxMath.chances(listOf(1, 2)))
        assertEquals(listOf(0.0, 100.0), MysteryBoxMath.chances(listOf(0, 4)))
        assertEquals(listOf(0.0, 0.0), MysteryBoxMath.chances(listOf(0, 0)))
    }

    @Test
    fun `a review shows the first name only`() {
        assertEquals("Misty", ReviewRules.firstName("Misty Waterflower"))
        assertEquals("Brock", ReviewRules.firstName("  Brock  "))
        assertEquals("Klant", ReviewRules.firstName("   "))
    }
}
