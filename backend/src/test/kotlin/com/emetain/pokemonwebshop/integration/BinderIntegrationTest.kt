package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.PokemonSpecies
import com.emetain.pokemonwebshop.repository.BinderSelectionRepository
import com.emetain.pokemonwebshop.repository.CardWishlistEntryRepository
import com.emetain.pokemonwebshop.repository.CollectionEntryRepository
import com.emetain.pokemonwebshop.repository.PokemonSpeciesRepository
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

class BinderIntegrationTest : CollectionTestSupport() {

    @Autowired
    private lateinit var entryRepository: CollectionEntryRepository

    @Autowired
    private lateinit var selectionRepository: BinderSelectionRepository

    @Autowired
    private lateinit var wishlistRepository: CardWishlistEntryRepository

    @Autowired
    private lateinit var speciesRepository: PokemonSpeciesRepository

    private lateinit var session: Cookie

    // Prices far above other tests, so the top of every list belongs to this test.
    private val top = 2_000_000_000L
    private val ids = List(11) { uniqueCode() }

    @BeforeEach
    fun importCardsWithPrices() {
        entryRepository.deleteAll()
        selectionRepository.deleteAll()
        wishlistRepository.deleteAll()
        session = loggedInUser()
        importCards(
            ids.mapIndexed { index, id ->
                providerCard(
                    id,
                    number = "${index + 1}",
                    name = "Card ${index + 1}",
                    prices = listOf(providerPrice(trend = top - index)),
                )
            },
        )
    }

    private fun binder(session: Cookie? = null, page: Int = 0): ResultActions {
        val request = get("/v1/binder?page=$page")
        return mockMvc.perform(if (session == null) request else request.cookie(session)).andExpect(status().isOk)
    }

    private fun choose(source: String) {
        mockMvc.perform(patch("/v1/account").cookie(session).json(mapOf("binderSource" to source))).andExpect(status().isOk)
    }

    private fun ResultActions.names(): List<String> =
        objectMapper.readTree(andReturn().response.contentAsString)["slots"].map { it["card"]["name"].asText() }

    @Test
    fun `a visitor sees the most expensive cards in general, of any game`() {
        val magic = uniqueCode()
        importCards(
            listOf(providerCard(magic, name = "Black Lotus", prices = listOf(providerPrice(trend = top * 2)))),
            game = Game.MTG,
        )

        val result = binder()
            .andExpect(jsonPath("$.source").value("MOST_EXPENSIVE"))
            .andExpect(jsonPath("$.fallback").value(false))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.slots.length()").value(9))

        assertEquals("Black Lotus", result.names().first())
        assertEquals(true, "Card 1" in result.names())
    }

    @Test
    fun `popular means the cards in the most collections`() {
        val inTwoCollections = card(ids[10]).id
        val inOneCollection = card(ids[9]).id
        add(session, inTwoCollections)
        add(session, inOneCollection)
        add(loggedInUser(), inTwoCollections)
        choose("POPULAR")

        assertEquals(listOf("Card 11", "Card 10", "Card 1"), binder(session).names().take(3))
    }

    @Test
    fun `two copies of one customer count as one collection`() {
        val first = card(ids[10]).id
        val second = card(ids[9]).id
        add(session, first, quantity = 5)
        add(session, second)
        add(loggedInUser(), second)
        choose("POPULAR")

        assertEquals(listOf("Card 10", "Card 11"), binder(session).names().take(2))
    }

    @Test
    fun `a new shop fills the popular list with the most expensive Pokémon cards`() {
        val magic = uniqueCode()
        importCards(
            listOf(providerCard(magic, name = "Black Lotus", prices = listOf(providerPrice(trend = top * 2)))),
            game = Game.MTG,
        )
        choose("POPULAR")

        assertEquals("Card 1", binder(session).names().first())
    }

    @Test
    fun `a card without an image isn't popular, because it would leave a hole in the hero`() {
        val noImage = uniqueCode()
        importCards(listOf(providerCard(noImage, name = "No image").copy(imageSmallUrl = null)))
        add(session, card(noImage).id)
        choose("POPULAR")

        assertEquals(false, "No image" in binder(session).names())
    }

    @Test
    fun `pages hold nine cards`() {
        val total = objectMapper.readTree(binder().andReturn().response.contentAsString)["totalPages"].asInt()

        val last = binder(page = total - 1)

        assertEquals(true, last.names().size in 1..9)
        binder(page = total).andExpect(jsonPath("$.slots.length()").value(0))
    }

    @Test
    fun `a logged-in customer sees their most expensive cards by default`() {
        add(session, card(ids[5]).id)
        add(session, card(ids[2]).id)

        val result = binder(session)
            .andExpect(jsonPath("$.source").value("MOST_EXPENSIVE_OWNED"))
            .andExpect(jsonPath("$.fallback").value(false))
            .andExpect(jsonPath("$.totalPages").value(1))

        assertEquals(listOf("Card 3", "Card 6"), result.names())
    }

    @Test
    fun `a customer without cards who chose nothing gets the most expensive cards instead of an empty binder`() {
        binder(session)
            .andExpect(jsonPath("$.source").value("MOST_EXPENSIVE"))
            .andExpect(jsonPath("$.fallback").value(true))
            .andExpect(jsonPath("$.slots.length()").value(9))
    }

    @Test
    fun `a choice always stays what the binder shows, even while it's empty`() {
        for (source in listOf("MOST_EXPENSIVE_OWNED", "OWN_SELECTION")) {
            choose(source)

            binder(session)
                .andExpect(jsonPath("$.source").value(source))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.slots.length()").value(0))
        }
    }

    @Test
    fun `the choice is saved on the account`() {
        choose("MOST_EXPENSIVE")

        mockMvc.perform(get("/v1/auth/me").cookie(session))
            .andExpect(jsonPath("$.binderSource").value("MOST_EXPENSIVE"))

        val result = binder(session).andExpect(jsonPath("$.source").value("MOST_EXPENSIVE"))
        assertEquals(true, "Card 1" in result.names())
    }

    @Test
    fun `an account starts on the default choice`() {
        mockMvc.perform(get("/v1/auth/me").cookie(session))
            .andExpect(jsonPath("$.binderSource").value("MOST_EXPENSIVE_OWNED"))
    }

    @Test
    fun `an own selection shows the chosen cards in the chosen order`() {
        choose("OWN_SELECTION")

        val first = card(ids[7]).id
        val second = card(ids[1]).id
        mockMvc.perform(put("/v1/binder/selection/$first").cookie(session)).andExpect(status().isNoContent)
        mockMvc.perform(put("/v1/binder/selection/$second").cookie(session)).andExpect(status().isNoContent)

        val result = binder(session)
            .andExpect(jsonPath("$.source").value("OWN_SELECTION"))
            .andExpect(jsonPath("$.fallback").value(false))
        assertEquals(listOf("Card 8", "Card 2"), result.names())

        mockMvc.perform(get("/v1/binder/selection").cookie(session))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Card 8"))

        mockMvc.perform(delete("/v1/binder/selection/$first").cookie(session)).andExpect(status().isNoContent)

        assertEquals(listOf("Card 2"), binder(session).names())
    }

    @Test
    fun `selecting a card twice keeps it once`() {
        val cardId = card(ids[0]).id

        repeat(2) { mockMvc.perform(put("/v1/binder/selection/$cardId").cookie(session)).andExpect(status().isNoContent) }

        mockMvc.perform(get("/v1/binder/selection").cookie(session)).andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `the Pokédex source shows empty pockets for Pokémon you don't have`() {
        speciesRepository.deleteAll()
        val dex = (3_000_000..3_999_999).random()
        speciesRepository.saveAll((0..9).map { PokemonSpecies(dex + it, "Pokémon $it", 1) })
        val owned = uniqueCode()
        importCards(listOf(providerCard(owned, name = "Owned", dexNumbers = setOf(dex + 1))))
        add(session, card(owned).id)
        choose("POKEDEX")

        binder(session)
            .andExpect(jsonPath("$.source").value("POKEDEX"))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.slots.length()").value(9))
            .andExpect(jsonPath("$.slots[0].name").value("Pokémon 0"))
            .andExpect(jsonPath("$.slots[0].card").doesNotExist())
            .andExpect(jsonPath("$.slots[1].dexNumber").value(dex + 1))
            .andExpect(jsonPath("$.slots[1].card.imageUrl").value("https://example.com/$owned/low.webp"))

        binder(session, page = 1).andExpect(jsonPath("$.slots.length()").value(1))
    }

    @Test
    fun `changing the binder needs a login`() {
        mockMvc.perform(get("/v1/binder/selection")).andExpect(status().isUnauthorized)
        mockMvc.perform(put("/v1/binder/selection/1")).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/v1/binder/selection/1")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a card that doesn't exist can't be selected`() {
        mockMvc.perform(put("/v1/binder/selection/999999999").cookie(session)).andExpect(status().isNotFound)
    }

}
