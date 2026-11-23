package com.emetain.pokemonwebshop.integration

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

/** The cards on a customer's wishlist that the shop sells right now, for the top of the home page (2026-11-23). */
class WantedForSaleIntegrationTest : ShopTestSupport() {

    @Test
    fun `it needs a login`() {
        mockMvc.perform(get("/v1/wishlist/cards/for-sale")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `one product per wanted card that can be bought, the cheapest copy, the newest arrival first`() {
        val customer = loggedInUser()
        val (pikachu, eevee, mew, charizard, snorlax) = List(5) { uniqueCode() }
        importCards(
            listOf(
                providerCard(pikachu, number = "1", name = "Pikachu"),
                providerCard(eevee, number = "2", name = "Eevee"),
                providerCard(mew, number = "3", name = "Mew"),
                providerCard(charizard, number = "4", name = "Charizard"),
                providerCard(snorlax, number = "5", name = "Snorlax"),
            ),
        )
        for (wanted in listOf(pikachu, eevee, mew, charizard)) {
            mockMvc.perform(put("/v1/wishlist/cards/${card(wanted).id}").cookie(customer)).andExpect(status().isNoContent)
        }

        // Pikachu twice: only the cheaper copy shows.
        stocked("$word Pikachu played", type = "SINGLE", priceCents = 500, extra = single(pikachu, "PLAYED"))
        stocked("$word Pikachu near mint", type = "SINGLE", priceCents = 2000, extra = single(pikachu, "NEAR_MINT"))
        // Eevee sold out, Mew not in the shop yet (a draft with stock), Snorlax not wanted: none of them show.
        createProduct("$word Eevee", type = "SINGLE", extra = single(eevee, "NEAR_MINT"))
        val draft = createProduct("$word Mew", type = "SINGLE", status = "DRAFT", extra = single(mew, "NEAR_MINT"))
        adjustStock(draft["id"].asLong(), 3).andExpect(status().isOk)
        stocked("$word Snorlax", type = "SINGLE", extra = single(snorlax, "NEAR_MINT"))
        // Charizard came in last, as a slab: it goes first.
        stocked(
            "$word Charizard PSA 10",
            type = "GRADED_SLAB",
            extra = mapOf("cardId" to card(charizard).id, "gradingCompany" to "PSA", "grade" to "10"),
        )

        val names = mockMvc.perform(get("/v1/wishlist/cards/for-sale").cookie(customer))
            .andExpect(status().isOk)
            .json().values().map { it["name"].asString() }
        assertEquals(listOf("$word Charizard PSA 10", "$word Pikachu played"), names)

        // Someone else's wishlist is empty.
        val other = mockMvc.perform(get("/v1/wishlist/cards/for-sale").cookie(loggedInUser())).andExpect(status().isOk).json()
        assertEquals(0, other.size())
    }

    @Test
    fun `a wanted card that sells out leaves the row`() {
        val customer = loggedInUser()
        val id = uniqueCode()
        importCards(listOf(providerCard(id, name = "Mewtwo")))
        mockMvc.perform(put("/v1/wishlist/cards/${card(id).id}").cookie(customer)).andExpect(status().isNoContent)
        val product = stocked("$word Mewtwo", type = "SINGLE", quantity = 1, extra = single(id, "NEAR_MINT"))

        assertEquals(1, mockMvc.perform(get("/v1/wishlist/cards/for-sale").cookie(customer)).json().size())

        adjustStock(product["id"].asLong(), -1, reason = "CORRECTION").andExpect(status().isOk)
        assertEquals(0, mockMvc.perform(get("/v1/wishlist/cards/for-sale").cookie(customer)).json().size())
    }

    private fun single(externalId: String, condition: String) = mapOf("cardId" to card(externalId).id, "condition" to condition)
}
