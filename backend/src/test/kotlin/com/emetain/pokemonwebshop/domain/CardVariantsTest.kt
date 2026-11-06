package com.emetain.pokemonwebshop.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardVariantsTest {

    private val set = CardSet(game = Game.POKEMON, externalId = "s", code = "s", name = "Set", series = "Base")
    private val card = Card(
        game = Game.POKEMON, set = set, externalId = "c", number = "1", name = "A", nameSearch = "a", sortKey = "000001",
    )

    private fun price(variant: String, trend: Long?) =
        CardPrice(card, variant, PriceSource.CARDMARKET, Currency.EUR, trendCents = trend, updatedAt = Instant.EPOCH)

    @Test
    fun `lists show the most common finish that has a trend price`() {
        val prices = listOf(price(CardVariants.REVERSE, 300), price(CardVariants.NORMAL, 100), price(CardVariants.HOLO, 200))

        assertEquals(100, CardVariants.displayPrice(prices)?.trendCents)
    }

    @Test
    fun `a finish with only an average is passed over`() {
        val prices = listOf(price(CardVariants.NORMAL, null), price(CardVariants.REVERSE, 300))

        assertEquals(300, CardVariants.displayPrice(prices)?.trendCents)
    }

    @Test
    fun `a card without any trend price has no display price`() {
        assertNull(CardVariants.displayPrice(listOf(price(CardVariants.NORMAL, null))))
        assertNull(CardVariants.displayPrice(emptyList()))
    }

    @Test
    fun `sorts variants in display order, unknown ones last`() {
        assertEquals(
            listOf(CardVariants.NORMAL, CardVariants.REVERSE, CardVariants.FIRST_EDITION, "unknown"),
            CardVariants.displaySorted(listOf("unknown", CardVariants.FIRST_EDITION, CardVariants.REVERSE, CardVariants.NORMAL)),
        )
    }
}
