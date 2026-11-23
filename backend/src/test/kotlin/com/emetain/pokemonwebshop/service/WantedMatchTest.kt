package com.emetain.pokemonwebshop.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WantedMatchTest {

    @Test
    fun `green for a card you don't have yet, within your maximum or without one`() {
        assertEquals(WantedMatch.WITHIN, WantedMatch.of(priceCents = 500, maxPriceCents = null, owned = false))
        assertEquals(WantedMatch.WITHIN, WantedMatch.of(priceCents = 500, maxPriceCents = 500, owned = false))
        assertEquals(WantedMatch.WITHIN, WantedMatch.of(priceCents = 400, maxPriceCents = 500, owned = false))
    }

    @Test
    fun `blue for a card you already have, within your maximum or without one`() {
        assertEquals(WantedMatch.OWNED, WantedMatch.of(priceCents = 500, maxPriceCents = null, owned = true))
        assertEquals(WantedMatch.OWNED, WantedMatch.of(priceCents = 500, maxPriceCents = 600, owned = true))
    }

    @Test
    fun `red above your maximum, also for a card you already have`() {
        assertEquals(WantedMatch.ABOVE_MAX, WantedMatch.of(priceCents = 501, maxPriceCents = 500, owned = false))
        assertEquals(WantedMatch.ABOVE_MAX, WantedMatch.of(priceCents = 501, maxPriceCents = 500, owned = true))
    }

    @Test
    fun `without a price nothing is above the maximum`() {
        assertEquals(WantedMatch.WITHIN, WantedMatch.of(priceCents = null, maxPriceCents = 500, owned = false))
    }
}
