package com.emetain.pokemonwebshop.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CartRulesTest {

    @Test
    fun `a line can go up to what is left, and never past 99`() {
        assertEquals(3, CartRules.maxQuantity(3))
        assertEquals(99, CartRules.maxQuantity(500))
        assertEquals(0, CartRules.maxQuantity(-2))
    }

    @Test
    fun `merging adds up both carts, up to what can be bought`() {
        assertEquals(3, CartRules.mergedQuantity(inAccount = 1, inGuestCart = 2, available = 10))
        assertEquals(4, CartRules.mergedQuantity(inAccount = 2, inGuestCart = 3, available = 4))
    }

    @Test
    fun `merging never takes away what the account already had`() {
        assertEquals(5, CartRules.mergedQuantity(inAccount = 5, inGuestCart = 1, available = 2))
    }

    @Test
    fun `a product that can't be bought keeps its quantity, so the cart shows it as sold out`() {
        assertEquals(3, CartRules.mergedQuantity(inAccount = 1, inGuestCart = 2, available = null))
        assertEquals(99, CartRules.mergedQuantity(inAccount = 60, inGuestCart = 60, available = null))
    }

    @Test
    fun `a line that can't be bought or asks for too many blocks checkout`() {
        assertEquals(CartProblem.SOLD_OUT, CartRules.problem(quantity = 1, canBuy = false, available = 5))
        assertEquals(CartProblem.TOO_MANY, CartRules.problem(quantity = 3, canBuy = true, available = 2))
        assertNull(CartRules.problem(quantity = 2, canBuy = true, available = 2))
    }
}
