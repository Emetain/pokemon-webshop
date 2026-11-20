package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.CardmarketLink
import com.emetain.pokemonwebshop.domain.CardmarketSingle
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.PriceSource
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.service.CardmarketSingleService
import com.emetain.pokemonwebshop.service.CatalogImportService
import com.emetain.pokemonwebshop.service.provider.ProviderCard
import com.emetain.pokemonwebshop.service.provider.ProviderPrice
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Phase 11: Pokémon and Magic cards that share a Cardmarket product get a price per print, and prices are kept per day. */
class CardmarketLinkIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var importService: CatalogImportService

    @Autowired
    private lateinit var singleService: CardmarketSingleService

    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var cardPriceRepository: CardPriceRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private val updated = Instant.parse("2026-11-19T01:48:38Z")

    // Product ids are unique across Cardmarket, and so across tests.
    // Always above Int.MAX_VALUE: JSON reads a smaller product id back as an Int, and an Int never equals the Long that
    // `jsonPath(...).value(base + 1)` expects. With the old range from 10 million that failed about one run in four.
    private val base = Random.nextLong(300_000_000, 900_000_000) * 10

    private fun single(offset: Long, trend: Long, name: String = "Mega Rayquaza ex [Roar of the Ruler]") = CardmarketSingle(
        productId = base + offset,
        game = Game.POKEMON,
        name = name,
        expansionId = base,
        metacardId = base + 9,
        trendCents = trend,
        averageCents = trend + 10,
        updatedAt = updated,
    )

    private fun rayquaza(code: String, number: String, tcgplayer: Long?) = ProviderCard(
        externalId = "$code-$number",
        number = number,
        name = "Mega Rayquaza ex",
        rarity = null,
        cardType = "Pokemon",
        imageSmallUrl = null,
        imageLargeUrl = null,
        variants = setOf(CardVariants.NORMAL),
        prices = listOf(ProviderPrice(CardVariants.NORMAL, 315, 392, updated)),
        cardmarketProductId = base + 1,
        fallbackPrices = listOfNotNull(
            tcgplayer?.let { ProviderPrice(CardVariants.NORMAL, it, null, updated, PriceSource.TCGPLAYER, Currency.USD) },
        ),
    )

    private fun card(externalId: String, game: Game = Game.POKEMON) =
        cardRepository.findAllByGameAndExternalIdIn(game, listOf(externalId)).single()

    private fun price(externalId: String) = cardPriceRepository.findAllByCardIds(listOf(card(externalId).id)).single()

    private fun magicSingle(offset: Long, name: String, trend: Long, foilTrend: Long? = null) = CardmarketSingle(
        productId = base + offset,
        game = Game.MTG,
        name = name,
        expansionId = base,
        metacardId = null,
        trendCents = trend,
        averageCents = null,
        foilTrendCents = foilTrend,
        updatedAt = updated,
    )

    private fun magic(code: String, number: String, name: String, product: Long, variants: Set<String>) = ProviderCard(
        externalId = "$code-$number",
        number = number,
        name = name,
        rarity = null,
        cardType = "Basic Land",
        imageSmallUrl = null,
        imageLargeUrl = null,
        variants = variants,
        // Scryfall's copy: the shared product's price for all of them.
        prices = listOf(ProviderPrice(CardVariants.NORMAL, 59342, null, updated)),
        cardmarketProductId = base + product,
    )

    private fun magicPrices(externalId: String) =
        cardPriceRepository.findAllByCardIds(listOf(card(externalId, Game.MTG).id)).associate { it.variant to it.trendCents }

    private fun history(externalId: String): List<Map<String, Any?>> =
        jdbc.queryForList("select * from card_price_history where card_id = ? order by price_date", card(externalId).id)

    @Test
    fun `cards that share a product get their own price, saved once per change`() {
        val code = uniqueCode()
        singleService.replace(Game.POKEMON, listOf(single(1, 293), single(2, 28545), single(3, 12377)))
        val cards = listOf(rayquaza(code, "084", 491), rayquaza(code, "138", 27729), rayquaza(code, "141", 21986))

        importService.importSet(Game.POKEMON, providerSet(code, cards))

        assertEquals(listOf(293L, 28545L, 12377L), listOf("084", "138", "141").map { price("$code-$it").trendCents })
        assertEquals(listOf(base + 1, base + 2, base + 3), listOf("084", "138", "141").map { card("$code-$it").cardmarketProductId })
        assertEquals(CardmarketLink.MATCHED, card("$code-138").cardmarketLink)
        assertEquals(28545L, card("$code-138").sortPriceCents)
        assertEquals(28545L, history("$code-138").single()["TREND_CENTS"] ?: history("$code-138").single()["trend_cents"])

        // The same prices again cost no row; a changed price on the same day replaces that day's row.
        importService.importSet(Game.POKEMON, providerSet(code, cards))
        singleService.replace(Game.POKEMON, listOf(single(1, 293), single(2, 30000), single(3, 12377)))
        importService.importSet(Game.POKEMON, providerSet(code, cards))

        assertEquals(1, history("$code-084").size)
        val row = history("$code-138").single()
        assertEquals(30000L, (row["TREND_CENTS"] ?: row["trend_cents"]) as Long)
    }

    @Test
    fun `an admin links a doubtful card by hand, and a sync keeps it`() {
        val code = uniqueCode()
        // Two cards share a product, and Cardmarket has only one Squirtle there: the order says nothing. The other one
        // is listed under another name, which only an admin can see.
        singleService.replace(Game.POKEMON, listOf(single(1, 2196, "Squirtle [Wave Splash]"), single(4, 5000, "Squirtle (Error) [Bubble]")))
        val squirtles = listOf("131", "132").map { number ->
            rayquaza(code, number, tcgplayer = null).copy(name = "Squirtle", cardmarketProductId = base + 1)
        }
        // The admin list is paged, newest set first, and other tests leave doubtful cards behind in the shared
        // database: the newest release date puts this card on the first page, whatever ran before.
        val set = providerSet(code, squirtles, releaseDate = LocalDate.of(2999, 12, 31))
        importService.importSet(Game.POKEMON, set)
        val doubtful = card("$code-131")
        assertEquals(CardmarketLink.DOUBTFUL, doubtful.cardmarketLink)
        assertEquals(emptyList(), cardPriceRepository.findAllByCardIds(listOf(doubtful.id)))

        val admin = loggedInUser(Role.ADMIN)
        mockMvc.perform(get("/v1/admin/catalog/cardmarket-links?status=doubtful").cookie(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.cardId == ${doubtful.id})].candidates[0].productId").value(base + 1))

        mockMvc.perform(put("/v1/admin/catalog/cardmarket-links/${doubtful.id}").cookie(admin).json(mapOf("productId" to base + 4)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.link").value("MANUAL"))
            .andExpect(jsonPath("$.prices[0].trendCents").value(5000))
            .andExpect(jsonPath("$.prices[0].source").value("CARDMARKET"))

        importService.importSet(Game.POKEMON, set)
        assertEquals(CardmarketLink.MANUAL, card("$code-131").cardmarketLink)
        assertEquals(base + 4, card("$code-131").cardmarketProductId)
        assertEquals(5000L, price("$code-131").trendCents)

        mockMvc.perform(put("/v1/admin/catalog/cardmarket-links/${doubtful.id}").cookie(admin).json(mapOf("productId" to 1L)))
            .andExpect(status().isBadRequest)
        mockMvc.perform(delete("/v1/admin/catalog/cardmarket-links/${doubtful.id}").cookie(admin))
            .andExpect(status().isNoContent)
        assertNull(card("$code-131").cardmarketLink)
        mockMvc.perform(get("/v1/admin/catalog/cardmarket-links?status=manual").cookie(loggedInUser()))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `Magic cards that share a product get their own, and a foil print listed apart keeps the foil column`() {
        val code = uniqueCode()
        singleService.replace(
            Game.MTG,
            listOf(
                magicSingle(1, "Island", 59342),
                magicSingle(2, "Island", 65581),
                magicSingle(3, "Island", 65454),
                magicSingle(4, "Duress", 64, foilTrend = 11950),
            ),
        )
        val islands = listOf("295", "296", "297").map { magic(code, it, "Island", 1, setOf(CardVariants.NORMAL)) }
        val duress = listOf(
            magic(code, "131", "Duress", 4, setOf(CardVariants.NORMAL)),
            magic(code, "131★", "Duress", 4, setOf(CardVariants.FOIL)),
        )

        importService.importSet(Game.MTG, providerSet(code, islands + duress))

        assertEquals(listOf(59342L, 65581L, 65454L), listOf("295", "296", "297").map { magicPrices("$code-$it")[CardVariants.NORMAL] })
        assertEquals(base + 2, card("$code-296", Game.MTG).cardmarketProductId)
        assertEquals(mapOf(CardVariants.NORMAL to 64L), magicPrices("$code-131"))
        assertEquals(mapOf(CardVariants.FOIL to 11950L), magicPrices("$code-131★"))
        assertEquals(CardmarketLink.SOURCE, card("$code-131★", Game.MTG).cardmarketLink)

        val admin = loggedInUser(Role.ADMIN)
        mockMvc.perform(get("/v1/admin/catalog/cardmarket-links?game=magic&status=doubtful").cookie(admin))
            .andExpect(status().isOk)
        mockMvc.perform(get("/v1/admin/catalog/cardmarket-links?game=lorcana").cookie(admin))
            .andExpect(status().isBadRequest)
        // A Magic card can't be linked to a product of another game.
        singleService.replace(Game.POKEMON, listOf(single(5, 100)))
        mockMvc.perform(put("/v1/admin/catalog/cardmarket-links/${card("$code-295", Game.MTG).id}").cookie(admin).json(mapOf("productId" to base + 5)))
            .andExpect(status().isBadRequest)
    }
}
