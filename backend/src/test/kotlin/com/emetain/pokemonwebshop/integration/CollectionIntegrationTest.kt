package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.ExchangeRate
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.PriceSource
import com.emetain.pokemonwebshop.repository.ExchangeRateRepository
import com.emetain.pokemonwebshop.service.provider.ProviderPrice
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals

class CollectionIntegrationTest : CollectionTestSupport() {

    @Autowired
    private lateinit var exchangeRateRepository: ExchangeRateRepository

    private lateinit var session: Cookie

    private val pikachuId = uniqueCode()
    private val raichuId = uniqueCode()
    private val mewId = uniqueCode()
    private lateinit var code: String

    @BeforeEach
    fun importSet() {
        exchangeRateRepository.save(ExchangeRate(Currency.USD, BigDecimal("1.25"), LocalDate.of(2026, 11, 5)))
        session = loggedInUser()
        code = importCards(
            listOf(
                providerCard(
                    pikachuId,
                    number = "25",
                    name = "Pikachu",
                    variants = setOf(CardVariants.NORMAL, CardVariants.REVERSE),
                    prices = listOf(
                        providerPrice(CardVariants.NORMAL, trend = 1000),
                        providerPrice(CardVariants.REVERSE, trend = 2500),
                    ),
                ),
                providerCard(raichuId, number = "26", name = "Raichu", prices = listOf(providerPrice(trend = 300))),
                providerCard(mewId, number = "151", name = "Mew"),
            ),
        )
    }

    private val pikachu get() = card(pikachuId)
    private val raichu get() = card(raichuId)
    private val mew get() = card(mewId)

    private fun progress(): ResultActions =
        mockMvc.perform(get("/v1/collection/pokemon/sets/$code").cookie(session)).andExpect(status().isOk)

    private fun cards(query: String): ResultActions =
        mockMvc.perform(get("/v1/catalog/pokemon/sets/$code/cards?$query").cookie(session)).andExpect(status().isOk)

    private fun ResultActions.names(): List<String> =
        objectMapper.readTree(andReturn().response.contentAsString)["items"].map { it["name"].asText() }

    @Test
    fun `everything about a collection needs a login`() {
        val requests = listOf(
            get("/v1/collection"),
            get("/v1/collection/pokemon/sets/$code"),
            get("/v1/collection/cards/1"),
            post("/v1/collection/entries").json(mapOf("cardId" to 1)),
            post("/v1/collection/entries/bulk").json(mapOf("cardIds" to listOf(1))),
            put("/v1/collection/entries/1").json(mapOf("quantity" to 1)),
            delete("/v1/collection/entries/1"),
            put("/v1/collection/share"),
        )
        requests.forEach { mockMvc.perform(it).andExpect(status().isUnauthorized) }
    }

    @Test
    fun `adding and removing a card updates progress and value right away`() {
        progress()
            .andExpect(jsonPath("$.progress.owned").value(0))
            .andExpect(jsonPath("$.progress.total").value(3))
            .andExpect(jsonPath("$.value.cents").value(0))

        val added = add(session, pikachu.id, CardVariants.NORMAL)
            .andExpect(jsonPath("$.entries.length()").value(1))
            .andExpect(jsonPath("$.progress.progress.owned").value(1))
            .andExpect(jsonPath("$.progress.progress.percent").value(33.3))
            .andExpect(jsonPath("$.progress.value.cents").value(1000))

        mockMvc.perform(put("/v1/collection/entries/${added.entryId(CardVariants.NORMAL)}").cookie(session).json(mapOf("quantity" to 3)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entries[0].quantity").value(3))
            .andExpect(jsonPath("$.progress.progress.owned").value(1))
            .andExpect(jsonPath("$.progress.value.cents").value(3000))

        mockMvc.perform(delete("/v1/collection/entries/${added.entryId(CardVariants.NORMAL)}").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entries.length()").value(0))
            .andExpect(jsonPath("$.progress.progress.owned").value(0))
            .andExpect(jsonPath("$.progress.value.cents").value(0))

        progress().andExpect(jsonPath("$.progress.owned").value(0))
    }

    @Test
    fun `a second copy of a card raises the quantity and not the progress`() {
        add(session, raichu.id)
        add(session, raichu.id)
            .andExpect(jsonPath("$.entries.length()").value(1))
            .andExpect(jsonPath("$.entries[0].quantity").value(2))
            .andExpect(jsonPath("$.progress.progress.owned").value(1))
    }

    @Test
    fun `normal progress counts a card once, master progress counts every variant`() {
        add(session, pikachu.id, CardVariants.NORMAL)
        add(session, pikachu.id, CardVariants.REVERSE)

        progress()
            .andExpect(jsonPath("$.progress.owned").value(1))
            .andExpect(jsonPath("$.progress.total").value(3))
            .andExpect(jsonPath("$.masterProgress.owned").value(2))
            .andExpect(jsonPath("$.masterProgress.total").value(4))
            .andExpect(jsonPath("$.masterProgress.percent").value(50.0))
    }

    @Test
    fun `cards that share a number count once for normal progress and separately for master progress`() {
        val base = uniqueCode()
        val alternate = uniqueCode()
        val onePiece = importCards(
            listOf(
                providerCard(base, number = "OP01-001", name = "Zoro"),
                providerCard(alternate, number = "OP01-001", name = "Zoro (Alternate Art)"),
                providerCard(number = "OP01-002", name = "Nami"),
            ),
            game = Game.ONE_PIECE,
        )

        add(session, card(alternate, Game.ONE_PIECE).id)

        mockMvc.perform(get("/v1/collection/one-piece/sets/$onePiece").cookie(session))
            .andExpect(jsonPath("$.progress.owned").value(1))
            .andExpect(jsonPath("$.progress.total").value(2))
            .andExpect(jsonPath("$.masterProgress.owned").value(1))
            .andExpect(jsonPath("$.masterProgress.total").value(3))
    }

    @Test
    fun `a complete set reaches 100 percent, even when the source announced more cards`() {
        val announced = importCards(listOf(providerCard(raichuId + "x", number = "1")))
        val set = cardSetRepository.findByGameAndCode(Game.POKEMON, announced)!!
        set.cardCount = 25
        cardSetRepository.save(set)

        add(session, card(raichuId + "x").id)

        mockMvc.perform(get("/v1/collection/pokemon/sets/$announced").cookie(session))
            .andExpect(jsonPath("$.progress.percent").value(100.0))
    }

    @Test
    fun `the value uses the owned variant and counts a variant without a price as unpriced`() {
        add(session, pikachu.id, CardVariants.REVERSE)
        add(session, mew.id, quantity = 2)

        progress()
            .andExpect(jsonPath("$.value.cents").value(2500))
            .andExpect(jsonPath("$.value.unpricedCards").value(2))
    }

    @Test
    fun `the overview adds up games and converts dollars to the chosen currency`() {
        val lorcana = uniqueCode()
        val dollars = ProviderPrice(CardVariants.NORMAL, 1000, null, PRICE_DATE, PriceSource.TCGPLAYER, Currency.USD)
        importCards(listOf(providerCard(lorcana, name = "Elsa", prices = listOf(dollars))), game = Game.LORCANA)
        add(session, raichu.id)
        add(session, card(lorcana, Game.LORCANA).id)

        mockMvc.perform(get("/v1/collection").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value.cents").value(1100))
            .andExpect(jsonPath("$.value.currency").value("EUR"))
            .andExpect(jsonPath("$.copies").value(2))
            .andExpect(jsonPath("$.games[?(@.game == 'pokemon')].value.cents").value(300))
            .andExpect(jsonPath("$.games[?(@.game == 'lorcana')].value.cents").value(800))
            .andExpect(jsonPath("$.sets.length()").value(2))
            .andExpect(jsonPath("$.mostValuable[0].name").value("Elsa"))
            .andExpect(jsonPath("$.mostValuable[0].trendCents").value(1000))
            .andExpect(jsonPath("$.mostValuable[0].currency").value("USD"))

        mockMvc.perform(patch("/v1/account").cookie(session).json(mapOf("currency" to "USD"))).andExpect(status().isOk)

        mockMvc.perform(get("/v1/collection").cookie(session))
            .andExpect(jsonPath("$.value.cents").value(1375))
            .andExpect(jsonPath("$.value.currency").value("USD"))
    }

    @Test
    fun `the overview of a customer without cards is empty and not an error`() {
        mockMvc.perform(get("/v1/collection").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.copies").value(0))
            .andExpect(jsonPath("$.value.cents").value(0))
            .andExpect(jsonPath("$.games.length()").value(4))
            .andExpect(jsonPath("$.sets.length()").value(0))
            .andExpect(jsonPath("$.mostValuable.length()").value(0))
    }

    @Test
    fun `started sets of a game show their progress`() {
        add(session, raichu.id)

        mockMvc.perform(get("/v1/collection/pokemon/sets").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].code").value(code))
            .andExpect(jsonPath("$[0].progress.owned").value(1))
            .andExpect(jsonPath("$[0].progress.total").value(3))

        mockMvc.perform(get("/v1/collection/magic/sets").cookie(session))
            .andExpect(jsonPath("$.length()").value(0))
    }

    /** Sends the same request from several threads at the same moment, and returns the statuses. */
    private fun atTheSameTime(times: Int, request: () -> Int): List<Int> {
        val pool = Executors.newFixedThreadPool(times)
        try {
            return pool.invokeAll(List(times) { Callable { request() } }).map { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `additions at the same moment all count, even for a card that wasn't in the collection yet`() {
        val cardId = mew.id

        val statuses = atTheSameTime(8) {
            mockMvc.perform(post("/v1/collection/entries").cookie(session).json(mapOf("cardId" to cardId)))
                .andReturn().response.status
        }

        assertEquals(List(8) { 201 }, statuses)
        mockMvc.perform(get("/v1/collection/cards/$cardId").cookie(session))
            .andExpect(jsonPath("$.entries.length()").value(1))
            .andExpect(jsonPath("$.entries[0].quantity").value(8))
    }

    @Test
    fun `a bulk add sent twice at the same moment adds every card once`() {
        val ids = listOf(pikachu.id, raichu.id, mew.id)

        val statuses = atTheSameTime(4) {
            mockMvc.perform(post("/v1/collection/entries/bulk").cookie(session).json(mapOf("cardIds" to ids)))
                .andReturn().response.status
        }

        assertEquals(List(4) { 200 }, statuses)
        progress().andExpect(jsonPath("$.progress.owned").value(3))
    }

    @Test
    fun `a double click on the wishlist or the binder doesn't fail`() {
        val cardId = raichu.id

        val wishlist = atTheSameTime(4) {
            mockMvc.perform(put("/v1/wishlist/cards/$cardId").cookie(session)).andReturn().response.status
        }
        val binder = atTheSameTime(4) {
            mockMvc.perform(put("/v1/binder/selection/$cardId").cookie(session)).andReturn().response.status
        }

        assertEquals(List(4) { 204 }, wishlist)
        assertEquals(List(4) { 204 }, binder)
    }

    @Test
    fun `an unknown game or set is not found`() {
        mockMvc.perform(get("/v1/collection/poker/sets").cookie(session)).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/collection/pokemon/sets/nope").cookie(session)).andExpect(status().isNotFound)
    }

    @Test
    fun `an entry of someone else can't be changed or removed`() {
        val entry = add(session, raichu.id).entryId(CardVariants.NORMAL)
        val other = loggedInUser()

        mockMvc.perform(put("/v1/collection/entries/$entry").cookie(other).json(mapOf("quantity" to 5)))
            .andExpect(status().isNotFound)
        mockMvc.perform(delete("/v1/collection/entries/$entry").cookie(other)).andExpect(status().isNotFound)

        progress().andExpect(jsonPath("$.progress.owned").value(1))
    }

    @Test
    fun `a collection is only visible to its owner`() {
        add(session, raichu.id)

        mockMvc.perform(get("/v1/collection/pokemon/sets/$code").cookie(loggedInUser()))
            .andExpect(jsonPath("$.progress.owned").value(0))
    }

    @Test
    fun `a condition can be set and cleared`() {
        val entry = add(session, raichu.id).entryId(CardVariants.NORMAL)

        mockMvc.perform(put("/v1/collection/entries/$entry").cookie(session).json(mapOf("quantity" to 1, "condition" to "NEAR_MINT")))
            .andExpect(jsonPath("$.entries[0].condition").value("NEAR_MINT"))

        mockMvc.perform(put("/v1/collection/entries/$entry").cookie(session).json(mapOf("quantity" to 1)))
            .andExpect(jsonPath("$.entries[0].condition").doesNotExist())
    }

    @Test
    fun `a whole list of cards can be added at once, and doing it twice changes nothing`() {
        add(session, pikachu.id, CardVariants.REVERSE)

        fun bulk() = mockMvc.perform(
            post("/v1/collection/entries/bulk").cookie(session).json(mapOf("cardIds" to listOf(pikachu.id, raichu.id, mew.id))),
        ).andExpect(status().isOk)

        bulk().andExpect(jsonPath("$.added").value(2)).andExpect(jsonPath("$.skipped").value(1))
        bulk().andExpect(jsonPath("$.added").value(0)).andExpect(jsonPath("$.skipped").value(3))

        progress()
            .andExpect(jsonPath("$.progress.owned").value(3))
            .andExpect(jsonPath("$.masterProgress.owned").value(3))
    }

    @Test
    fun `an empty bulk list is refused`() {
        mockMvc.perform(post("/v1/collection/entries/bulk").cookie(session).json(mapOf("cardIds" to emptyList<Long>())))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `card lists show what the viewer owns`() {
        add(session, pikachu.id, CardVariants.REVERSE, quantity = 2)

        cards("")
            .andExpect(jsonPath("$.items[0].owned[0].variant").value(CardVariants.REVERSE))
            .andExpect(jsonPath("$.items[0].owned[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].variants.length()").value(2))
            .andExpect(jsonPath("$.items[1].owned.length()").value(0))

        mockMvc.perform(get("/v1/catalog/pokemon/sets/$code/cards"))
            .andExpect(jsonPath("$.items[0].owned").doesNotExist())
    }

    @Test
    fun `card lists filter on owned and missing cards`() {
        add(session, pikachu.id, CardVariants.NORMAL)

        assertEquals(listOf("Pikachu"), cards("collection=owned").names())
        assertEquals(listOf("Raichu", "Mew"), cards("collection=missing").names().sortedDescending())
    }

    @Test
    fun `in master mode a card is missing until every variant is owned`() {
        add(session, pikachu.id, CardVariants.NORMAL)
        assertEquals(setOf("Pikachu", "Raichu", "Mew"), cards("collection=missing&master=true").names().toSet())

        add(session, pikachu.id, CardVariants.REVERSE)
        assertEquals(setOf("Raichu", "Mew"), cards("collection=missing&master=true").names().toSet())
    }

    @Test
    fun `a card with a covered number isn't missing in normal mode`() {
        val base = uniqueCode()
        val alternate = uniqueCode()
        val onePiece = importCards(
            listOf(
                providerCard(base, number = "OP01-001", name = "Zoro"),
                providerCard(alternate, number = "OP01-001", name = "Zoro (Alternate Art)"),
                providerCard(number = "OP01-002", name = "Nami"),
            ),
            game = Game.ONE_PIECE,
        )
        add(session, card(alternate, Game.ONE_PIECE).id)

        val missing = mockMvc.perform(get("/v1/catalog/one-piece/sets/$onePiece/cards?collection=missing").cookie(session))

        assertEquals(listOf("Nami"), missing.names())
    }

    @Test
    fun `the collection filter means nothing to a visitor who isn't logged in`() {
        val result = mockMvc.perform(get("/v1/catalog/pokemon/sets/$code/cards?collection=owned"))

        assertEquals(3, result.names().size)
    }

    @Test
    fun `the card endpoint tells what the customer has of a card`() {
        add(session, raichu.id, quantity = 2)
        mockMvc.perform(put("/v1/wishlist/cards/${raichu.id}").cookie(session)).andExpect(status().isNoContent)
        mockMvc.perform(put("/v1/binder/selection/${raichu.id}").cookie(session)).andExpect(status().isNoContent)

        mockMvc.perform(get("/v1/collection/cards/${raichu.id}").cookie(session))
            .andExpect(jsonPath("$.entries[0].quantity").value(2))
            .andExpect(jsonPath("$.onWishlist").value(true))
            .andExpect(jsonPath("$.inBinder").value(true))

        mockMvc.perform(get("/v1/collection/cards/${mew.id}").cookie(session))
            .andExpect(jsonPath("$.entries.length()").value(0))
            .andExpect(jsonPath("$.onWishlist").value(false))
            .andExpect(jsonPath("$.inBinder").value(false))
    }
}
