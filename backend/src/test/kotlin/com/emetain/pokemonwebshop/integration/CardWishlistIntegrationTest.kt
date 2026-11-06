package com.emetain.pokemonwebshop.integration

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class CardWishlistIntegrationTest : CollectionTestSupport() {

    @Test
    fun `the wishlist needs a login`() {
        mockMvc.perform(get("/v1/wishlist/cards")).andExpect(status().isUnauthorized)
        mockMvc.perform(put("/v1/wishlist/cards/1")).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/v1/wishlist/cards/1")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `cards can be added and removed, most valuable first`() {
        val session = loggedInUser()
        val cheap = uniqueCode()
        val expensive = uniqueCode()
        importCards(
            listOf(
                providerCard(cheap, number = "1", name = "Cheap", prices = listOf(providerPrice(trend = 100))),
                providerCard(expensive, number = "2", name = "Expensive", prices = listOf(providerPrice(trend = 9000))),
            ),
        )

        mockMvc.perform(put("/v1/wishlist/cards/${card(cheap).id}").cookie(session)).andExpect(status().isNoContent)
        mockMvc.perform(put("/v1/wishlist/cards/${card(expensive).id}").cookie(session)).andExpect(status().isNoContent)

        mockMvc.perform(get("/v1/wishlist/cards").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(2))
            .andExpect(jsonPath("$.items[0].name").value("Expensive"))
            .andExpect(jsonPath("$.items[1].name").value("Cheap"))

        mockMvc.perform(delete("/v1/wishlist/cards/${card(expensive).id}").cookie(session)).andExpect(status().isNoContent)

        mockMvc.perform(get("/v1/wishlist/cards").cookie(session))
            .andExpect(jsonPath("$.totalItems").value(1))
    }

    @Test
    fun `adding or removing twice is fine`() {
        val session = loggedInUser()
        val id = uniqueCode()
        importCards(listOf(providerCard(id)))
        val cardId = card(id).id

        repeat(2) { mockMvc.perform(put("/v1/wishlist/cards/$cardId").cookie(session)).andExpect(status().isNoContent) }
        mockMvc.perform(get("/v1/wishlist/cards").cookie(session)).andExpect(jsonPath("$.totalItems").value(1))

        repeat(2) { mockMvc.perform(delete("/v1/wishlist/cards/$cardId").cookie(session)).andExpect(status().isNoContent) }
        mockMvc.perform(get("/v1/wishlist/cards").cookie(session)).andExpect(jsonPath("$.totalItems").value(0))
    }

    @Test
    fun `a card that doesn't exist can't be added`() {
        mockMvc.perform(put("/v1/wishlist/cards/999999999").cookie(loggedInUser())).andExpect(status().isNotFound)
    }

    @Test
    fun `every customer has their own wishlist`() {
        val id = uniqueCode()
        importCards(listOf(providerCard(id)))

        mockMvc.perform(put("/v1/wishlist/cards/${card(id).id}").cookie(loggedInUser())).andExpect(status().isNoContent)

        mockMvc.perform(get("/v1/wishlist/cards").cookie(loggedInUser())).andExpect(jsonPath("$.totalItems").value(0))
    }
}
