package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.CardVariants.NORMAL
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceSource
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OptcgMapperTest {

    private fun row(setId: String?, imageId: String, name: String = "Luffy", setName: String = "Some Set"): JsonNode = json(
        """
        {
          "set_id": ${setId?.let { "\"$it\"" }}, "set_name": "$setName", "card_image_id": "$imageId",
          "card_set_id": "${imageId.substringBefore('_')}", "card_name": "$name", "rarity": "SR", "card_type": "Character",
          "card_image": "https://optcgapi.com/$imageId.jpg", "market_price": 3.72, "inventory_price": 3.22,
          "date_scraped": "2026-11-05"
        }
        """,
    )

    private fun lists(
        sets: List<JsonNode> = listOf(json("""{ "set_id": "OP-01", "set_name": "Romance Dawn" }""")),
        decks: List<JsonNode> = listOf(json("""{ "structure_deck_id": "ST-01", "structure_deck_name": "Straw Hat Crew" }""")),
        setCards: List<JsonNode> = emptyList(),
        deckCards: List<JsonNode> = emptyList(),
        promoCards: List<JsonNode> = emptyList(),
        donCards: List<JsonNode> = emptyList(),
    ) = OptcgLists(sets, decks, setCards, deckCards, promoCards, donCards)

    private fun List<ProviderSet>.code(code: String) = first { it.code == code }

    @Test
    fun `maps the card fields`() {
        val card = OptcgMapper.sets(lists(setCards = listOf(row("OP-01", "OP01-077_p1", "Perona")))).code("OP-01").cards.single()

        assertEquals("OP-01|OP01-077_p1|Perona", card.externalId)
        assertEquals("OP01-077", card.number)
        assertEquals("Perona", card.name)
        assertEquals("SR", card.rarity)
        assertEquals("Character", card.cardType)
        assertEquals("https://optcgapi.com/OP01-077_p1.jpg", card.imageSmallUrl)
        assertEquals(setOf(NORMAL), card.variants)
    }

    @Test
    fun `market_price is the tcgplayer price in dollars, dated by its own scrape date`() {
        val card = OptcgMapper.sets(lists(setCards = listOf(row("OP-01", "OP01-001")))).code("OP-01").cards.single()

        assertEquals(
            listOf(ProviderPrice(NORMAL, 372, null, Instant.parse("2026-11-05T00:00:00Z"), PriceSource.TCGPLAYER, Currency.USD)),
            card.prices,
        )
    }

    @Test
    fun `promos and don cards get a price too`() {
        val sets = OptcgMapper.sets(lists(promoCards = listOf(row("P", "P-001")), donCards = listOf(row("OP-14", "don_1"))))

        assertEquals(372, sets.code("P").cards.single().prices.single().trendCents)
        assertEquals(372, sets.code("DON").cards.single().prices.single().trendCents)
    }

    @Test
    fun `zero means no price`() {
        val row = json(
            """{ "set_id": "OP-01", "card_set_id": "A", "card_image_id": "A", "card_name": "A", "market_price": 0.0, "date_scraped": "2026-11-05" }""",
        )

        assertTrue(OptcgMapper.sets(lists(setCards = listOf(row))).code("OP-01").cards.single().prices.isEmpty())
    }

    @Test
    fun `a price without a date can't be shown honestly, so it isn't stored`() {
        val row = json(
            """{ "set_id": "OP-01", "card_set_id": "A", "card_image_id": "A", "card_name": "A", "market_price": 1.5 }""",
        )

        assertTrue(OptcgMapper.sets(lists(setCards = listOf(row))).code("OP-01").cards.single().prices.isEmpty())
    }

    @Test
    fun `the card type is the category and two colours are split`() {
        val row = json(
            """{ "set_id": "OP-01", "card_set_id": "A", "card_image_id": "A", "card_name": "A", "card_type": "Leader", "card_color": "Green Red" }""",
        )

        val card = OptcgMapper.sets(lists(setCards = listOf(row))).code("OP-01").cards.single()

        assertEquals("Leader", card.category)
        assertEquals(setOf("Green", "Red"), card.colors)
    }

    @Test
    fun `a value that isn't a colour is left out`() {
        val row = json(
            """{ "set_id": "OP-01", "card_set_id": "A", "card_image_id": "A", "card_name": "A", "card_color": "Character" }""",
        )

        assertTrue(OptcgMapper.sets(lists(setCards = listOf(row))).code("OP-01").cards.single().colors.isEmpty())
    }

    @Test
    fun `a card without an image has no image urls`() {
        val row = json(
            """{ "set_id": "OP-01", "card_set_id": "OP01-1", "card_image_id": "OP01-1", "card_name": "A", "card_image": null }""",
        )

        assertNull(OptcgMapper.sets(lists(setCards = listOf(row))).code("OP-01").cards.single().imageSmallUrl)
    }

    @Test
    fun `the same image id stays apart per set and per name`() {
        val rows = listOf(row("OP-01", "X-1", "Zoro"), row("OP-01", "X-1", "Nami"))
        val deckRows = listOf(row("ST-01", "X-1", "Zoro"))

        val sets = OptcgMapper.sets(lists(setCards = rows, deckCards = deckRows))

        assertEquals(2, sets.code("OP-01").cards.size)
        assertEquals(1, sets.code("ST-01").cards.size)
    }

    @Test
    fun `exact copies are dropped and don't count`() {
        val set = OptcgMapper.sets(lists(deckCards = listOf(row("ST-01", "ST01-1"), row("ST-01", "ST01-1")))).code("ST-01")

        assertEquals(1, set.cards.size)
        assertEquals(1, set.cardCount)
    }

    @Test
    fun `all promos go into one set, whatever their own set id is`() {
        val promos = listOf(row("P", "P-001"), row("OP01", "OP01-004"), row("ST03", "ST03-014_pr5"))

        val set = OptcgMapper.sets(lists(promoCards = promos)).code("P")

        assertEquals(3, set.cards.size)
        assertEquals("PROMO", set.series)
    }

    @Test
    fun `don cards go into one set and use the image id as their number`() {
        val row = json(
            """{ "set_id": null, "card_image_id": "don_171", "card_name": "DON!! Card (Sakazuki)", "rarity": "DON!!", "card_set_id": null }""",
        )

        val set = OptcgMapper.sets(lists(donCards = listOf(row))).code("DON")

        assertEquals("don_171", set.cards.single().number)
        assertEquals("|don_171|DON!! Card (Sakazuki)", set.cards.single().externalId)
        assertEquals("DON", set.series)
    }

    @Test
    fun `a booster that only exists through its cards gets its name from them`() {
        val rows = listOf(row("EB-05", "EB05-001", setName = "Extra Booster: One Piece Heroines Edition Vol. 2"))

        val set = OptcgMapper.sets(lists(setCards = rows)).code("EB-05")

        assertEquals("Extra Booster: One Piece Heroines Edition Vol. 2", set.name)
        assertEquals("EB", set.series)
    }

    @Test
    fun `when cards disagree about their set, the most common name wins`() {
        val rows = listOf(
            row("ST-29", "ST29-1", setName = "Starter Deck 29: Egghead"),
            row("ST-29", "ST29-2", setName = "Starter Deck 29: Egghead"),
            row("ST-29", "ST29-3", setName = "The World's Strongest Warriors"),
        )

        assertEquals("Starter Deck 29: Egghead", OptcgMapper.sets(lists(deckCards = rows)).code("ST-29").name)
    }

    @Test
    fun `newest boosters come first, then decks, promos and don cards`() {
        val sets = listOf(
            json("""{ "set_id": "OP-01", "set_name": "Romance Dawn" }"""),
            json("""{ "set_id": "EB-01", "set_name": "Memorial" }"""),
            json("""{ "set_id": "OP-02", "set_name": "Paramount War" }"""),
        )
        val decks = listOf(
            json("""{ "structure_deck_id": "ST-28", "structure_deck_name": "Deck 28" }"""),
            json("""{ "structure_deck_id": "ST-30", "structure_deck_name": "Deck 30" }"""),
        )
        val deckCards = listOf(row("ST-29", "ST29-1", setName = "Deck 29"))
        val setCards = listOf(row("EB-05", "EB05-1", setName = "Heroines"))

        val newestFirst = OptcgMapper.sets(lists(sets = sets, decks = decks, setCards = setCards, deckCards = deckCards))
            .sortedByDescending { it.sortOrder }
            .map { it.code }

        assertEquals(listOf("OP-02", "EB-05", "EB-01", "OP-01", "ST-30", "ST-29", "ST-28", "P", "DON"), newestFirst)
    }

    @Test
    fun `several unlisted boosters of one series keep their number order`() {
        val setCards = listOf(row("EB-06", "EB06-1"), row("EB-05", "EB05-1"))
        val sets = listOf(json("""{ "set_id": "EB-01", "set_name": "Memorial" }"""), json("""{ "set_id": "OP-02", "set_name": "War" }"""))

        val newestFirst = OptcgMapper.sets(lists(sets = sets, setCards = setCards))
            .sortedByDescending { it.sortOrder }
            .map { it.code }

        assertEquals(listOf("OP-02", "EB-06", "EB-05", "EB-01"), newestFirst.take(4))
    }

    @Test
    fun `series come from the prefix of the code`() {
        val sets = listOf(
            json("""{ "set_id": "OP14-EB04", "set_name": "A" }"""),
            json("""{ "set_id": "PRB-01", "set_name": "B" }"""),
        )

        val result = OptcgMapper.sets(lists(sets = sets))

        assertEquals("OP", result.code("OP14-EB04").series)
        assertEquals("PRB", result.code("PRB-01").series)
        assertEquals("ST", result.code("ST-01").series)
    }

    @Test
    fun `sets have no release date, logo or symbol`() {
        val set = OptcgMapper.sets(lists()).code("OP-01")

        assertNull(set.releaseDate)
        assertNull(set.logoUrl)
        assertNull(set.symbolUrl)
    }
}
