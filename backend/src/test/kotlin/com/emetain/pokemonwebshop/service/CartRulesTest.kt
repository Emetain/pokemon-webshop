package com.emetain.pokemonwebshop.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CartRulesTest {

    @Test
    fun `the maximum per line is 10`() {
        assertEquals(10, CartRules.MAX_PER_LINE)
    }

    @Test
    fun `merging adds up both carts, up to the maximum per line`() {
        assertEquals(3, CartRules.mergedQuantity(inAccount = 1, inGuestCart = 2))
        assertEquals(10, CartRules.mergedQuantity(inAccount = 6, inGuestCart = 6))
    }

    @Test
    fun `merging never takes away what the account already had`() {
        // A line from before the maximum was lowered keeps its quantity, and the cart shows it as too many.
        assertEquals(15, CartRules.mergedQuantity(inAccount = 15, inGuestCart = 1))
    }

    @Test
    fun `a line that can't be bought or is above the maximum blocks checkout`() {
        assertEquals(CartProblem.SOLD_OUT, CartRules.problem(quantity = 1, canBuy = false))
        assertEquals(CartProblem.TOO_MANY, CartRules.problem(quantity = 11, canBuy = true))
        assertNull(CartRules.problem(quantity = 10, canBuy = true))
    }
}
