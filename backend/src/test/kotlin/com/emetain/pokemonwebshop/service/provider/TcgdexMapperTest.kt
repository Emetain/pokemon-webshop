package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.CardVariants.FIRST_EDITION
import com.emetain.pokemonwebshop.domain.CardVariants.HOLO
import com.emetain.pokemonwebshop.domain.CardVariants.NORMAL
import com.emetain.pokemonwebshop.domain.CardVariants.REVERSE
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TcgdexMapperTest {

    private fun card(variants: String, market: String?, detailed: String = "[]") = TcgdexMapper.card(
        json(
            """
            {
              "id": "x-1", "localId": "1", "name": "Testmon",
              "variants": { $variants },
              "pricing": ${market?.let { """{ "cardmarket": { "updated": "2026-11-05T13:07:54.166Z", "idProduct": 1, $it } }""" } ?: "{}"},
              "variants_detailed": $detailed
            }
            """,
        ),
    )

    private fun ProviderCard.trend(variant: String) = prices.firstOrNull { it.variant == variant }?.trendCents

    private val bothColumns = """ "trend": 10, "avg": 11, "trend-holo": 20, "avg-holo": 21 """

    // The rules, in the order of docs/phases/phase-02.md.

    @Test
    fun `reads the top level price when variants_detailed has none`() {
        val card = TcgdexMapper.card(fixture("tcgdex-sm12-1.json"))

        assertEquals(listOf(NORMAL), card.prices.map { it.variant })
        assertEquals(1937, card.trend(NORMAL))
    }

    @Test
    fun `the base fields belong to normal`() {
        val card = card(""" "normal": true """, bothColumns)

        assertEquals(1000, card.trend(NORMAL))
        assertEquals(1100, card.prices.single().averageCents)
    }

    @Test
    fun `the base fields belong to holo when there is no normal`() {
        val card = card(""" "holo": true """, bothColumns)

        assertEquals(listOf(HOLO), card.prices.map { it.variant })
        assertEquals(1000, card.trend(HOLO))
    }

    @Test
    fun `the holo fields belong to reverse`() {
        val card = card(""" "normal": true, "reverse": true """, bothColumns)

        assertEquals(1000, card.trend(NORMAL))
        assertEquals(2000, card.trend(REVERSE))
    }

    @Test
    fun `a card without a reverse doesn't get one from the holo fields`() {
        val charizard = TcgdexMapper.card(fixture("tcgdex-base1-4.json"))

        assertNull(charizard.trend(REVERSE))
        assertEquals(35381, charizard.trend(HOLO))
    }

    @Test
    fun `normal and holo without a reverse split the two columns`() {
        val card = card(""" "normal": true, "holo": true """, bothColumns)

        assertEquals(1000, card.trend(NORMAL))
        assertEquals(2000, card.trend(HOLO))
    }

    @Test
    fun `with normal, holo and reverse the holo gets no price`() {
        val card = card(""" "normal": true, "holo": true, "reverse": true """, bothColumns)

        assertEquals(setOf(NORMAL, REVERSE), card.prices.map { it.variant }.toSet())
    }

    @Test
    fun `holo and reverse without a normal use one column each`() {
        val card = card(""" "holo": true, "reverse": true """, bothColumns)

        assertEquals(1000, card.trend(HOLO))
        assertEquals(2000, card.trend(REVERSE))
    }

    @Test
    fun `a reverse on its own uses the holo fields`() {
        assertEquals(2000, card(""" "reverse": true """, bothColumns).trend(REVERSE))
    }

    @Test
    fun `a reverse on its own falls back to the base fields`() {
        assertEquals(1000, card(""" "reverse": true """, """ "trend": 10, "trend-holo": null """).trend(REVERSE))
    }

    @Test
    fun `the 1st edition has a price when it is its own product`() {
        val charizard = TcgdexMapper.card(fixture("tcgdex-base1-4.json"))

        assertEquals(348925, charizard.trend(FIRST_EDITION))
        assertEquals(340000, charizard.prices.first { it.variant == FIRST_EDITION }.averageCents)
    }

    @Test
    fun `a 1st edition entry on the same product is not a price of its own`() {
        val lugia = TcgdexMapper.card(fixture("tcgdex-neo1-9.json"))

        assertTrue(FIRST_EDITION in lugia.variants)
        assertNull(lugia.trend(FIRST_EDITION))
    }

    @Test
    fun `other stamps, subtypes and jumbo sizes are ignored`() {
        val detailed = """
            [
              { "stamp": ["pokemon-center"], "size": "standard", "pricing": { "cardmarket": { "idProduct": 2, "trend": 5 } } },
              { "subtype": "shadowless", "size": "standard", "pricing": { "cardmarket": { "idProduct": 3, "trend": 6 } } },
              { "stamp": ["1st-edition"], "size": "jumbo", "pricing": { "cardmarket": { "idProduct": 4, "trend": 7 } } }
            ]
        """
        val card = card(""" "normal": true, "firstEdition": true """, """ "trend": 10 """, detailed)

        assertEquals(listOf(NORMAL), card.prices.map { it.variant })
    }

    @Test
    fun `a card that isn't a 1st edition never gets that price`() {
        val detailed = """[{ "stamp": ["1st-edition"], "pricing": { "cardmarket": { "idProduct": 2, "trend": 5 } } }]"""

        assertNull(card(""" "normal": true """, """ "trend": 10 """, detailed).trend(FIRST_EDITION))
    }

    @Test
    fun `zero and null mean no price`() {
        assertTrue(card(""" "normal": true """, """ "trend": 0, "avg": null """).prices.isEmpty())
    }

    @Test
    fun `a variant with only an average keeps it`() {
        val price = card(""" "normal": true """, """ "trend": 0, "avg": 12.5 """).prices.single()

        assertNull(price.trendCents)
        assertEquals(1250, price.averageCents)
    }

    @Test
    fun `the price date is the updated field`() {
        val card = card(""" "normal": true """, """ "trend": 1 """)

        assertEquals(Instant.parse("2026-11-05T13:07:54.166Z"), card.prices.single().updatedAt)
    }

    @Test
    fun `a card without cardmarket data has no prices`() {
        assertTrue(card(""" "normal": true """, null).prices.isEmpty())
    }

    @Test
    fun `maps the card fields`() {
        val card = TcgdexMapper.card(fixture("tcgdex-sm12-1.json"))

        assertEquals("sm12-1", card.externalId)
        assertEquals("1", card.number)
        assertEquals("Venusaur & Snivy GX", card.name)
        assertEquals("Ultra Rare", card.rarity)
        assertEquals("Pokemon", card.cardType)
        assertEquals(setOf(NORMAL), card.variants)
        assertEquals(setOf(3, 495), card.dexNumbers)
        assertEquals("https://assets.tcgdex.net/en/sm/sm12/1/low.webp", card.imageSmallUrl)
        assertEquals("https://assets.tcgdex.net/en/sm/sm12/1/high.webp", card.imageLargeUrl)
    }

    @Test
    fun `a pokémon has its energy types and category`() {
        val charizard = TcgdexMapper.card(
            json("""{ "id": "x", "localId": "1", "name": "Charizard", "category": "Pokemon", "types": ["Fire"] }"""),
        )

        assertEquals("Pokemon", charizard.category)
        assertEquals(setOf("Fire"), charizard.colors)
    }

    @Test
    fun `a basic energy card gets its type from its name`() {
        val energy = TcgdexMapper.card(
            json("""{ "id": "x", "localId": "98", "name": "Fire Energy", "category": "Energy", "energyType": "Normal" }"""),
        )

        assertEquals("Energy", energy.category)
        assertEquals(setOf("Fire"), energy.colors)
    }

    @Test
    fun `a special energy card with another name has no type`() {
        val energy = TcgdexMapper.card(
            json("""{ "id": "x", "localId": "96", "name": "Double Colorless Energy", "category": "Energy" }"""),
        )

        assertTrue(energy.colors.isEmpty())
    }

    @Test
    fun `a trainer card has no type, even if its name sounds like one`() {
        val trainer = TcgdexMapper.card(
            json("""{ "id": "x", "localId": "92", "name": "Fire Energy", "category": "Trainer" }"""),
        )

        assertEquals("Trainer", trainer.category)
        assertTrue(trainer.colors.isEmpty())
    }

    @Test
    fun `a card without an image has no image urls`() {
        val card = card(""" "normal": true """, null)

        assertNull(card.imageSmallUrl)
        assertNull(card.imageLargeUrl)
    }

    @Test
    fun `the rarity None means no rarity`() {
        val card = TcgdexMapper.card(json("""{ "id": "x", "localId": "1", "name": "A", "rarity": "None" }"""))

        assertNull(card.rarity)
    }

    @Test
    fun `maps a set`() {
        val set = TcgdexMapper.set(fixture("tcgdex-set-base1.json"), sortOrder = 7, cards = emptyList(), failedCards = 2)

        assertEquals("base1", set.externalId)
        assertEquals("base1", set.code)
        assertEquals("Base Set", set.name)
        assertEquals("Base", set.series)
        assertEquals(LocalDate.of(1999, 1, 9), set.releaseDate)
        assertEquals(102, set.cardCount)
        assertEquals("https://assets.tcgdex.net/en/base/base1/logo.webp", set.logoUrl)
        assertNull(set.symbolUrl)
        assertEquals(7, set.sortOrder)
        assertEquals(2, set.failedCards)
    }

    // Phase 11: the shop's own Cardmarket link needs TCGdex's product id and TCGplayer's price per print.

    @Test
    fun `keeps the Cardmarket product and the TCGplayer price of the print`() {
        val card = TcgdexMapper.card(fixture("tcgdex-me06-138.json"))

        assertEquals(918912L, card.cardmarketProductId)
        // TCGdex's placeholder says "normal"; TCGplayer's "holofoil" is the real finish (2026-11-20).
        assertEquals(setOf(HOLO), card.variants)
        val fallback = card.fallbackPrices.single()
        assertEquals(HOLO, fallback.variant)
        assertEquals(27729, fallback.trendCents)
        assertEquals(com.emetain.pokemonwebshop.domain.PriceSource.TCGPLAYER, fallback.source)
        assertEquals(com.emetain.pokemonwebshop.domain.Currency.USD, fallback.currency)
    }

    @Test
    fun `maps TCGplayer finishes to the card's variants`() {
        val card = TcgdexMapper.card(
            json(
                """
                {
                  "id": "x-2", "localId": "2", "name": "Testmon",
                  "variants": { "normal": true, "reverse": true, "firstEdition": true },
                  "pricing": { "tcgplayer": { "updated": "2026-11-19T05:47:44.244Z",
                    "unlimited": { "marketPrice": 1.50 },
                    "reverse-holofoil": { "marketPrice": 0, "midPrice": 2.25 },
                    "1st-edition": { "marketPrice": 9.99 } } }
                }
                """,
            ),
        )

        val byVariant = card.fallbackPrices.associate { it.variant to it.trendCents }
        assertEquals(mapOf(NORMAL to 150L, REVERSE to 225L, FIRST_EDITION to 999L), byVariant)
        assertNull(card.cardmarketProductId)
    }

    // 2026-11-20: TCGdex's placeholder variants for a new set ("normal" only, variantId "generated").

    private val placeholder = """ "normal": true, "holo": false, "reverse": false """
    private val generated = """[{ "type": "normal", "size": "standard", "variantId": "generated" }]"""

    private fun placeholderCard(rarity: String, tcgplayer: String?) = TcgdexMapper.card(
        json(
            """
            {
              "id": "me06-1", "localId": "1", "name": "Testmon", "rarity": "$rarity",
              "variants": { $placeholder },
              "variants_detailed": $generated,
              "pricing": {
                "cardmarket": { "updated": "2026-11-19T00:39:57.000Z", "idProduct": 1, $bothColumns }
                ${tcgplayer?.let { """, "tcgplayer": { "updated": "2026-11-19T05:47:44.244Z", $it }""" } ?: ""}
              }
            }
            """,
        ),
    )

    @Test
    fun `a placeholder takes the finishes TCGplayer has prices for`() {
        val card = placeholderCard("Common", """ "normal": { "marketPrice": 0.10 }, "reverse-holofoil": { "marketPrice": 0.30 } """)

        assertEquals(setOf(NORMAL, REVERSE), card.variants)
        // Cardmarket's second column is then the reverse holo, not nothing.
        assertEquals(1000L, card.trend(NORMAL))
        assertEquals(2000L, card.trend(REVERSE))
    }

    @Test
    fun `a placeholder without TCGplayer prices follows the rarity`() {
        assertEquals(setOf(NORMAL, REVERSE), placeholderCard("Uncommon", null).variants)
        assertEquals(setOf(HOLO, REVERSE), placeholderCard("Rare", null).variants)
        assertEquals(setOf(HOLO), placeholderCard("Double rare", null).variants)
        assertEquals(setOf(HOLO), placeholderCard("Special illustration rare", null).variants)
        assertEquals(setOf(HOLO), placeholderCard("Mega Hyper Rare", null).variants)
        // A rarity it doesn't know keeps what TCGdex says.
        assertEquals(setOf(NORMAL), placeholderCard("Promo", null).variants)
    }

    @Test
    fun `real variants stay as TCGdex gives them, also when TCGplayer differs`() {
        val card = TcgdexMapper.card(
            json(
                """
                {
                  "id": "sv03.5-1", "localId": "1", "name": "Testmon", "rarity": "Common",
                  "variants": { "normal": true, "reverse": true },
                  "variants_detailed": [{ "type": "normal", "variantId": "endfynwn4n10gzq" }, { "type": "reverse", "variantId": "cm4kqul3x1bwlz1f" }],
                  "pricing": { "tcgplayer": { "updated": "2026-11-19T05:47:44.244Z", "normal": { "marketPrice": 0.10 } } }
                }
                """,
            ),
        )

        assertEquals(setOf(NORMAL, REVERSE), card.variants)
    }
}
