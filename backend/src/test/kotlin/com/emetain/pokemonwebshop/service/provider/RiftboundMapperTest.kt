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

class RiftboundMapperTest {

    private val fetchedAt = Instant.parse("2026-11-12T03:00:00Z")

    private fun groups(): List<RiftboundGroup> {
        val fixture = fixture("tcgcsv-riftbound.json")
        return fixture.path("groups").map { group ->
            val id = group.path("groupId").asText()
            RiftboundGroup(group, fixture.path("products").path(id).toList(), fixture.path("prices").path(id).toList())
        }
    }

    @Test
    fun `a group is a set, its numbered products are the cards, and groups without cards stay out`() {
        val sets = RiftboundMapper.sets(groups(), fetchedAt)

        val set = sets.single()
        assertEquals("24439", set.externalId)
        assertEquals("OGS", set.code)
        assertEquals("Origins: Proving Grounds", set.name)
        assertEquals(LocalDate.of(2025, 10, 31), set.releaseDate)
        assertEquals(RiftboundMapper.SPECIAL_SERIES, set.series)
        assertEquals(listOf("Annie, Fiery", "Firestorm"), set.cards.map { it.name })
        assertEquals(2, set.cardCount)
    }

    @Test
    fun `maps the card fields, with the large image from TCGplayer's image server`() {
        val card = RiftboundMapper.sets(groups(), fetchedAt).single().cards.first()

        assertEquals("653136", card.externalId)
        assertEquals("001", card.number)
        assertEquals("Epic", card.rarity)
        assertEquals("Champion Unit", card.cardType)
        assertEquals("Champion Unit", card.category)
        assertEquals(setOf("Fury"), card.colors)
        assertEquals("https://tcgplayer-cdn.tcgplayer.com/product/653136_200w.jpg", card.imageSmallUrl)
        assertEquals("https://tcgplayer-cdn.tcgplayer.com/product/653136_in_1000x1000.jpg", card.imageLargeUrl)
        assertEquals(setOf(NORMAL), card.variants)
        assertEquals(listOf(ProviderPrice(NORMAL, 426, null, fetchedAt, PriceSource.TCGPLAYER, Currency.USD)), card.prices)
    }

    @Test
    fun `foil and normal printings each get their market price, and a printing without one gets none`() {
        val card = RiftboundMapper.card(
            json("""{ "productId": 1, "name": "Jinx", "imageUrl": null,
                "extendedData": [{ "name": "Number", "value": "202/298" }, { "name": "Card Type", "value": "Unit;Token" },
                                 { "name": "Domain", "value": "Fury;Chaos" }] }"""),
            listOf(
                json("""{ "productId": 1, "marketPrice": 1.5, "subTypeName": "Normal" }"""),
                json("""{ "productId": 1, "marketPrice": 12.25, "subTypeName": "Foil" }"""),
                json("""{ "productId": 1, "marketPrice": null, "subTypeName": "Holofoil" }"""),
            ),
            fetchedAt,
        )

        assertEquals(setOf(NORMAL, FOIL), card.variants)
        assertEquals(listOf(150L, 1225L), card.prices.map { it.trendCents })
        assertEquals("Unit - Token", card.cardType)
        assertEquals("Unit", card.category)
        assertEquals(setOf("Fury", "Chaos"), card.colors)
    }

    @ParameterizedTest
    @CsvSource(
        "001/298, 001",
        "126b/298, 126b",
        "T1A 001/005, T1A 001",
        "001*/005, 001*",
    )
    fun `the number is the part before the slash`(raw: String, expected: String) {
        val card = json("""{ "extendedData": [{ "name": "Number", "value": "$raw" }] }""")

        assertEquals(expected, RiftboundMapper.number(card))
    }

    @ParameterizedTest
    @CsvSource(
        "OGN, OGN",
        "RXK/DA, RXK-DA",
        "' T12025 ', T12025",
    )
    fun `set codes are safe in a URL`(abbreviation: String, expected: String) {
        assertEquals(expected, RiftboundMapper.code(abbreviation))
    }

    @ParameterizedTest
    @CsvSource(
        "Origins, OGN, 358, main",
        "Origins: Proving Grounds, OGS, 28, special",
        "Riftbound Promotional Cards, PR, 12, promo",
        "Riftbound Organized Play Promotional Cards, OPP, 247, promo",
        "T1 2025 Worlds Champion Bundles, T12025, 15, special",
    )
    fun `main sets, special sets and promos`(name: String, code: String, cards: Int, series: String) {
        assertEquals(series, RiftboundMapper.seriesOf(name, code, cards))
    }
}
