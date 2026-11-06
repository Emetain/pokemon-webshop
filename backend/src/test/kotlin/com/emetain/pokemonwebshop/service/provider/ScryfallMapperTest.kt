package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.CardVariants.ETCHED
import com.emetain.pokemonwebshop.domain.CardVariants.FOIL
import com.emetain.pokemonwebshop.domain.CardVariants.NORMAL
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScryfallMapperTest {

    private val updatedAt = Instant.parse("2026-11-05T10:06:51Z")

    private fun card(fields: String) = ScryfallMapper.card(
        json("""{ "id": "abc", "collector_number": "7", "name": "Bolt", $fields }"""),
        updatedAt,
    )

    @Test
    fun `a double-faced card shows its front`() {
        val card = ScryfallMapper.card(fixture("scryfall-double-faced.json"), updatedAt)

        assertEquals("Delver of Secrets // Insectile Aberration", card.name)
        assertEquals("51", card.number)
        assertEquals("Common", card.rarity)
        assertEquals("Creature — Human Wizard // Creature — Human Insect", card.cardType)
        assertTrue(card.imageSmallUrl!!.contains("/normal/front/"))
        assertTrue(card.imageLargeUrl!!.contains("/large/front/"))
    }

    @Test
    fun `eur is the trend of normal and eur_foil of foil, without an average`() {
        val card = ScryfallMapper.card(fixture("scryfall-double-faced.json"), updatedAt)

        assertEquals(
            listOf(ProviderPrice(NORMAL, 64, null, updatedAt), ProviderPrice(FOIL, 318, null, updatedAt)),
            card.prices,
        )
    }

    @Test
    fun `reads images from the card itself when it has them`() {
        val card = card(""" "image_uris": { "normal": "n.jpg", "large": "l.jpg" } """)

        assertEquals("n.jpg", card.imageSmallUrl)
        assertEquals("l.jpg", card.imageLargeUrl)
    }

    @Test
    fun `a card without images has no image urls`() {
        assertNull(card(""" "finishes": ["nonfoil"] """).imageSmallUrl)
    }

    @Test
    fun `finishes become variants`() {
        assertEquals(setOf(NORMAL, FOIL, ETCHED), card(""" "finishes": ["nonfoil", "foil", "etched"] """).variants)
    }

    @Test
    fun `a card without a finish never gets the price of it`() {
        val card = card(""" "finishes": ["foil"], "prices": { "eur": "1.00", "eur_foil": "2.00" } """)

        assertEquals(listOf(FOIL), card.prices.map { it.variant })
    }

    @Test
    fun `a missing eur price means no price`() {
        assertTrue(card(""" "finishes": ["nonfoil"], "prices": { "usd": "1.00", "eur": null } """).prices.isEmpty())
    }

    @Test
    fun `colour letters become colour names`() {
        assertEquals(setOf("White", "Blue", "Black", "Red", "Green"), card(""" "colors": ["W", "U", "B", "R", "G"] """).colors)
    }

    @Test
    fun `a card without colours is colourless`() {
        assertEquals(setOf("Colorless"), card(""" "colors": [] """).colors)
        assertEquals(setOf("Colorless"), card(""" "rarity": "rare" """).colors)
    }

    @Test
    fun `a double-faced card takes its colours from the front`() {
        assertEquals(setOf("Blue"), ScryfallMapper.card(fixture("scryfall-double-faced.json"), updatedAt).colors)
    }

    @ParameterizedTest
    @CsvSource(
        "Creature — Human Wizard,Creature",
        "Legendary Creature — Elf Druid,Creature",
        "Artifact Creature — Golem,Creature",
        "Instant,Instant",
        "Basic Land — Forest,Land",
        "Legendary Planeswalker — Jace,Planeswalker",
        "Artifact — Equipment,Artifact",
        "Enchantment — Aura,Enchantment",
        "Sorcery // Instant,Sorcery",
        "Creature — Human Wizard // Land,Creature",
    )
    fun `the type line gives the category`(typeLine: String, category: String) {
        assertEquals(category, card(""" "type_line": "$typeLine" """).category)
    }

    @Test
    fun `an unknown type has no category`() {
        assertNull(card(""" "type_line": "Card" """).category)
        assertNull(card(""" "rarity": "rare" """).category)
    }

    @Test
    fun `maps a set`() {
        val set = ScryfallMapper.set(
            json(
                """
                {
                  "id": "uuid", "code": "isd", "name": "Innistrad", "set_type": "expansion",
                  "released_at": "2011-09-30", "card_count": 276, "icon_svg_uri": "isd.svg"
                }
                """,
            ),
            sortOrder = 3,
            cards = emptyList(),
        )

        assertEquals("uuid", set.externalId)
        assertEquals("isd", set.code)
        assertEquals("expansion", set.series)
        assertEquals(LocalDate.of(2011, 9, 30), set.releaseDate)
        assertEquals(276, set.cardCount)
        assertNull(set.logoUrl)
        assertEquals("isd.svg", set.symbolUrl)
    }

    @Test
    fun `recognises digital cards and sets`() {
        assertTrue(ScryfallMapper.isDigital(json("""{ "digital": true }""")))
        assertFalse(ScryfallMapper.isDigital(json("""{ "digital": false }""")))
    }
}
