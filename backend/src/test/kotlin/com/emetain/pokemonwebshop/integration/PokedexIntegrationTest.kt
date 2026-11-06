package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.PokemonSpecies
import com.emetain.pokemonwebshop.repository.PokemonSpeciesRepository
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PokedexIntegrationTest : CollectionTestSupport() {

    @Autowired
    private lateinit var speciesRepository: PokemonSpeciesRepository

    private lateinit var session: Cookie
    private val pikachu = uniqueCode()
    private val rare = uniqueCode()
    private val duo = uniqueCode()

    // Far above the real Pokédex, so these slots belong to this test.
    private val dex = (2_000_000..2_999_999).random()

    @BeforeEach
    fun importCardsAndSpecies() {
        speciesRepository.deleteAll()
        speciesRepository.saveAll(
            listOf(PokemonSpecies(dex, "Pikachu", 1), PokemonSpecies(dex + 1, "Raichu", 1), PokemonSpecies(dex + 2, "Chikorita", 2)),
        )
        session = loggedInUser()
        importCards(
            listOf(
                providerCard(pikachu, number = "1", name = "Pikachu", dexNumbers = setOf(dex), prices = listOf(providerPrice(trend = 100))),
                providerCard(
                    rare,
                    number = "2",
                    name = "Pikachu Rare",
                    dexNumbers = setOf(dex),
                    rarity = "Ultra Rare",
                    prices = listOf(providerPrice(trend = 900)),
                ),
                providerCard(duo, number = "3", name = "Pikachu & Raichu", dexNumbers = setOf(dex, dex + 1)),
                providerCard(number = "4", name = "Professor Oak", category = "Trainer"),
            ),
        )
    }

    @Test
    fun `a visitor who isn't logged in sees every slot empty`() {
        mockMvc.perform(get("/v1/pokedex"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.progress.owned").value(0))
            .andExpect(jsonPath("$.progress.total").value(3))
            .andExpect(jsonPath("$.slots[0].name").value("Pikachu"))
            .andExpect(jsonPath("$.slots[0].filled").value(false))
    }

    @Test
    fun `owning a card fills its slot, and removing it empties the slot again`() {
        val entry = add(session, card(pikachu).id).entryId(CardVariants.NORMAL)

        mockMvc.perform(get("/v1/pokedex").cookie(session))
            .andExpect(jsonPath("$.progress.owned").value(1))
            .andExpect(jsonPath("$.progress.percent").value(33.3))
            .andExpect(jsonPath("$.generations[0].generation").value(1))
            .andExpect(jsonPath("$.generations[0].progress.owned").value(1))
            .andExpect(jsonPath("$.generations[0].progress.total").value(2))
            .andExpect(jsonPath("$.generations[1].progress.owned").value(0))
            .andExpect(jsonPath("$.slots[0].filled").value(true))
            .andExpect(jsonPath("$.slots[0].imageUrl").value("https://example.com/$pikachu/low.webp"))
            .andExpect(jsonPath("$.slots[1].filled").value(false))

        mockMvc.perform(delete("/v1/collection/entries/$entry").cookie(session)).andExpect(status().isOk)

        mockMvc.perform(get("/v1/pokedex").cookie(session))
            .andExpect(jsonPath("$.progress.owned").value(0))
    }

    @Test
    fun `a card with two Pokémon fills both slots`() {
        add(session, card(duo).id)

        mockMvc.perform(get("/v1/pokedex").cookie(session))
            .andExpect(jsonPath("$.progress.owned").value(2))
            .andExpect(jsonPath("$.slots[0].filled").value(true))
            .andExpect(jsonPath("$.slots[1].filled").value(true))
    }

    @Test
    fun `a slot shows the image of the most valuable owned card`() {
        add(session, card(pikachu).id)
        add(session, card(rare).id)

        mockMvc.perform(get("/v1/pokedex").cookie(session))
            .andExpect(jsonPath("$.slots[0].imageUrl").value("https://example.com/$rare/low.webp"))
    }

    @Test
    fun `one Pokémon shows its neighbours and how many of its cards you have`() {
        add(session, card(pikachu).id)

        mockMvc.perform(get("/v1/pokedex/${dex + 1}").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Raichu"))
            .andExpect(jsonPath("$.totalCards").value(1))
            .andExpect(jsonPath("$.ownedCards").value(0))
            .andExpect(jsonPath("$.previous.name").value("Pikachu"))
            .andExpect(jsonPath("$.next.name").value("Chikorita"))

        mockMvc.perform(get("/v1/pokedex/$dex").cookie(session))
            .andExpect(jsonPath("$.totalCards").value(3))
            .andExpect(jsonPath("$.ownedCards").value(1))
            .andExpect(jsonPath("$.previous").doesNotExist())
            .andExpect(jsonPath("$.rarities.length()").value(2))

        mockMvc.perform(get("/v1/pokedex/$dex"))
            .andExpect(jsonPath("$.ownedCards").doesNotExist())
    }

    @Test
    fun `the cards of a Pokémon start with the most valuable and can be filtered`() {
        add(session, card(pikachu).id)

        mockMvc.perform(get("/v1/pokedex/$dex/cards").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(3))
            .andExpect(jsonPath("$.items[0].name").value("Pikachu Rare"))
            .andExpect(jsonPath("$.items[1].name").value("Pikachu"))
            .andExpect(jsonPath("$.items[1].owned[0].quantity").value(1))

        mockMvc.perform(get("/v1/pokedex/$dex/cards?rarity=Ultra Rare").cookie(session))
            .andExpect(jsonPath("$.totalItems").value(1))

        mockMvc.perform(get("/v1/pokedex/$dex/cards?collection=owned").cookie(session))
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].name").value("Pikachu"))
    }

    @Test
    fun `a Pokémon that doesn't exist is not found`() {
        mockMvc.perform(get("/v1/pokedex/1")).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/pokedex/1/cards")).andExpect(status().isNotFound)
    }
}
