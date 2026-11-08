package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.ProductType
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardmarketProductMapperTest {

    private val products = CardmarketProductMapper.parse(
        ObjectMapper().readTree(javaClass.getResourceAsStream("/shop/cardmarket-nonsingles.json")),
    )

    private val sets = listOf(
        CatalogSetName(1, "Surging Sparks"),
        CatalogSetName(2, "151"),
        CatalogSetName(3, "Base Set"),
        CatalogSetName(4, "Base Set 2"),
    )

    @Test
    fun `products without an id are left out and doubled quotes are undone`() {
        assertEquals(20, products.size)
        assertEquals("Star Trek \"Beam Me Up\" Fat Pack Bundle", products.single { it.id == 601L }.name)
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "Pokémon Booster|Surging Sparks Booster|BOOSTER_PACK",
            "Pokémon Booster|Surging Sparks Sleeved Booster|SLEEVED_BOOSTER",
            "Pokémon Display|Surging Sparks Booster Box|BOOSTER_BOX",
            "Pokémon Display|Surging Sparks Booster Bundle|BOOSTER_BUNDLE",
            "Pokémon Elite Trainer Boxes|151 Elite Trainer Box|ELITE_TRAINER_BOX",
            "Pokémon Tins|Gengar Tin|TIN",
            "Pokémon Theme Decks|Zap! Theme Deck|STARTER_DECK",
            "Magic Display|Foundations Play Booster Box|BOOSTER_BOX",
            "Magic Fatpack|Foundations Fat Pack Bundle|BOOSTER_BUNDLE",
            "One Piece Preconstructed Decks|Starter Deck: Red Zoro|STARTER_DECK",
            "Lorcana Gift Set|Elsa Gift Box|COLLECTION_BOX",
        ],
    )
    fun `categories become product types`(category: String, name: String, type: ProductType) {
        assertEquals(type, CardmarketProductMapper.typeOf(category, name))
    }

    @Test
    fun `coins, lots and complete sets are not products the shop sells`() {
        assertNull(CardmarketProductMapper.typeOf("Pokémon Coins", "Pikachu Coin"))
        assertNull(CardmarketProductMapper.typeOf("MtG Set", "Alpha Complete Set"))
        assertNull(CardmarketProductMapper.typeOf("One Piece Lots", "Lot"))
        assertNull(CardmarketProductMapper.typeOf("Magic Theme Deck Display", "Display"))
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "Surging Sparks Booster Bundle Pokémon Center Version|BOOSTER_BUNDLE",
            "Surging Sparks 6 Booster Box Case|BOOSTER_BOX",
            "30th Celebration JP Booster Box|BOOSTER_BOX",
            "CSV10.5C: Black Bolt S-Ch Booster|BOOSTER_PACK",
            "Starter Deck: Blue Kuzan (Asia Region Legal)|STARTER_DECK",
            "Mega Evolution ID/TH Special Set Chikorita 2-Pack Blister|BLISTER",
            "Phantasmal Flames Build & Battle Box Display|COLLECTION_BOX",
            "Wilds Unknown Japanese Booster Box|BOOSTER_BOX",
            "Reality Fracture Promo Pack|BOOSTER_PACK",
        ],
    )
    fun `products from outside Europe and cartons of other products stay out`(name: String, type: ProductType) {
        assertFalse(CardmarketProductMapper.soldInEurope(name, type))
    }

    @Test
    fun `a booster display is a booster box`() {
        assertTrue(CardmarketProductMapper.soldInEurope("Duskmourn Play Booster Display", ProductType.BOOSTER_BOX))
        assertTrue(CardmarketProductMapper.soldInEurope("Evolving Skies Elite Trainer Box [LUJF]", ProductType.ELITE_TRAINER_BOX))
    }

    @Test
    fun `an expansion is linked to the set most of its products start with`() {
        val plan = CardmarketProductMapper.plan(products, sets)
        val byId = plan.products.associateBy { it.product.id }

        assertEquals(setOf(101L, 102L, 103L, 104L, 108L, 109L, 301L, 302L, 401L, 402L), byId.keys)
        assertEquals(1L, byId.getValue(101).setId)
        assertEquals(ProductType.SLEEVED_BOOSTER, byId.getValue(102).type)
        assertEquals(2L, byId.getValue(301).setId)
        assertEquals(3L, byId.getValue(401).setId)
        assertEquals(4L, byId.getValue(402).setId, "The longest set name wins")
    }

    @Test
    fun `the plan counts why products were left out`() {
        val plan = CardmarketProductMapper.plan(products, sets)

        assertEquals(20, plan.inList)
        // The coin, and a fat pack would be a type, but its set isn't in the catalog.
        assertEquals(1, plan.skippedType)
        // Case, Pokémon Center, Build & Battle display.
        assertEquals(3, plan.skippedMarket)
        // Japanese 151 (2), the loose products of expansion 1645 (3) and the Magic product without a set.
        assertEquals(6, plan.skippedNoSet)
    }

    @Test
    fun `game ids match the file names at Cardmarket`() {
        assertEquals(6, CardmarketProductMapper.gameId(Game.POKEMON))
        assertEquals(1, CardmarketProductMapper.gameId(Game.MTG))
        assertEquals(18, CardmarketProductMapper.gameId(Game.ONE_PIECE))
        assertEquals(19, CardmarketProductMapper.gameId(Game.LORCANA))
    }
}
