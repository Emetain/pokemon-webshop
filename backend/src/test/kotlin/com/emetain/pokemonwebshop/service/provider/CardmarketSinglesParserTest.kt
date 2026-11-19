package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.Game
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardmarketSinglesParserTest {

    private val mapper = ObjectMapper()

    private val products = """
        {"version":1,"createdAt":"2026-11-18T14:02:36+0100","products":[
          {"idProduct":918912,"name":"Mega Rayquaza ex [Roar of the Ruler | Storm Emeralda]","idCategory":51,
           "categoryName":"Pokémon Single","idExpansion":6704,"idMetacard":466225,"dateAdded":"2026-10-24 21:51:38"},
          {"idProduct":918982,"name":"Mega Rayquaza ex [Roar of the Ruler | Storm Emeralda]","idExpansion":6704,"idMetacard":466225},
          {"idProduct":0,"name":"Broken","idExpansion":1},
          {"idProduct":5,"name":"No metacard","idExpansion":1,"idMetacard":0}
        ]}
    """.trimIndent()

    private val guide = """
        {"version":1,"createdAt":"2026-11-19T02:48:38+0100","priceGuides":[
          {"idProduct":918912,"idCategory":51,"avg":3.92,"low":1,"trend":2.93,"avg1":3.26,"avg-holo":null,"trend-holo":0},
          {"idProduct":918982,"avg":290,"trend":285.45,"avg-holo":1.5,"trend-holo":1.25,"nested":{"ignored":[1,2]}}
        ]}
    """.trimIndent()

    @Test
    fun `reads products and prices entry by entry`() {
        val products = CardmarketSinglesParser.products(mapper.createParser(products))
        val guide = CardmarketSinglesParser.priceGuide(mapper.createParser(guide))

        assertEquals(listOf(918912L, 918982L, 5L), products.entries.map { it.id })
        assertNull(products.entries.last().metacardId)
        assertEquals(Instant.parse("2026-11-19T01:48:38Z"), guide.createdAt)

        val singles = CardmarketSinglesParser.combine(Game.POKEMON, products, guide, Instant.EPOCH)
        val rayquaza = singles.first { it.productId == 918982L }
        assertEquals(28545L, rayquaza.trendCents)
        assertEquals(29000L, rayquaza.averageCents)
        assertEquals(125L, rayquaza.foilTrendCents)
        assertEquals(466225L, rayquaza.metacardId)
        assertEquals(Instant.parse("2026-11-19T01:48:38Z"), rayquaza.updatedAt)
        // Cardmarket writes 0 for "no price".
        assertNull(singles.first { it.productId == 918912L }.foilTrendCents)
        assertNull(singles.first { it.productId == 5L }.trendCents)
    }
}
