package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.CardVariants.FOIL
import com.emetain.pokemonwebshop.domain.CardVariants.NORMAL
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LorcastMapperTest {

    private val fetchedAt = Instant.parse("2026-11-06T03:00:00Z")

    private fun card(fields: String) = LorcastMapper.card(
        json("""{ "id": "crd_1", "name": "Elsa", "collector_number": "5", $fields }"""),
        fetchedAt,
    )

    @Test
    fun `maps the card fields`() {
        val card = LorcastMapper.card(fixture("lorcast-card.json"), fetchedAt)

        assertEquals("crd_d9f3b86af85f48579ed9d0d7ce0de129", card.externalId)
        assertEquals("1", card.number)
        assertEquals("Ariel - On Human Legs", card.name)
        assertEquals("Uncommon", card.rarity)
        assertEquals("Character", card.cardType)
        assertTrue(card.imageSmallUrl!!.contains("/digital/normal/"))
        assertTrue(card.imageLargeUrl!!.contains("/digital/large/"))
    }

    @Test
    fun `usd prices are tcgplayer prices in dollars, dated at the moment we fetched them`() {
        val card = LorcastMapper.card(fixture("lorcast-card.json"), fetchedAt)

        assertEquals(setOf(NORMAL, FOIL), card.variants)
        assertEquals(
            listOf(
                ProviderPrice(NORMAL, 13, null, fetchedAt, PriceSource.TCGPLAYER, Currency.USD),
                ProviderPrice(FOIL, 72, null, fetchedAt, PriceSource.TCGPLAYER, Currency.USD),
            ),
            card.prices,
        )
    }

    @Test
    fun `a finish without a price has no price`() {
        val card = card(""" "prices": { "usd": null, "usd_foil": "9.99" } """)

        assertEquals(listOf(FOIL), card.prices.map { it.variant })
        assertEquals(999, card.prices.single().trendCents)
    }

    @Test
    fun `a name without a version stays as it is`() {
        assertEquals("Elsa", card(""" "version": null """).name)
        assertEquals("Elsa", card(""" "rarity": "Rare" """).name)
    }

    @Test
    fun `the first type is the category`() {
        assertEquals("Action", card(""" "type": ["Action", "Song"] """).category)
    }

    @Test
    fun `the ink is the colour, and dual inks count for both`() {
        assertEquals(setOf("Amber"), card(""" "ink": "Amber", "inks": null """).colors)
        assertEquals(setOf("Amber", "Ruby"), card(""" "ink": null, "inks": ["Amber", "Ruby"] """).colors)
    }

    @Test
    fun `shows rarities with a space`() {
        assertEquals("Super rare", card(""" "rarity": "Super_rare" """).rarity)
    }

    @Test
    fun `joins multiple types`() {
        assertEquals("Action - Song", card(""" "type": ["Action", "Song"] """).cardType)
    }

    @Test
    fun `a card with only a foil price exists in foil only`() {
        assertEquals(setOf(FOIL), card(""" "prices": { "usd": null, "usd_foil": "9.99" } """).variants)
    }

    @Test
    fun `a card without any price counts as normal`() {
        assertEquals(setOf(NORMAL), card(""" "prices": { "usd": null, "usd_foil": null } """).variants)
        assertEquals(setOf(NORMAL), card(""" "rarity": "Rare" """).variants)
    }

    @ParameterizedTest
    @CsvSource("1,main", "14,main", "P1,special", "cp,special", "D23,special", "CC1,special")
    fun `numeric codes are the main sets`(code: String, series: String) {
        val set = LorcastMapper.set(json("""{ "id": "s", "code": "$code", "name": "Set" }"""), 0, emptyList())

        assertEquals(series, set.series)
    }

    @Test
    fun `a set counts its own cards, because the source doesn't`() {
        val set = LorcastMapper.set(
            json("""{ "id": "set_1", "code": "1", "name": "The First Chapter", "released_at": "2023-08-18" }"""),
            sortOrder = 1,
            cards = listOf(card(""" "rarity": "Rare" """)),
        )

        assertEquals(1, set.cardCount)
        assertEquals(LocalDate.of(2023, 8, 18), set.releaseDate)
        assertNull(set.logoUrl)
        assertNull(set.symbolUrl)
    }
}
