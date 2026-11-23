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
            .json().values().map { it["product"]["name"].asString() }
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

    @Test
    fun `each card gets the colour of its frame from the customer's maximum and collection`() {
        val customer = loggedInUser()
        val (free, owned, dear, ownedDear, cheap) = List(5) { uniqueCode() }
        importCards(
            listOf(
                providerCard(free, number = "1", name = "Free"),
                providerCard(owned, number = "2", name = "Owned"),
                providerCard(dear, number = "3", name = "Dear"),
                providerCard(ownedDear, number = "4", name = "OwnedDear"),
                providerCard(cheap, number = "5", name = "Cheap"),
            ),
        )
        mockMvc.perform(put("/v1/wishlist/cards/${card(free).id}").cookie(customer)).andExpect(status().isNoContent)
        mockMvc.perform(put("/v1/wishlist/cards/${card(owned).id}").cookie(customer)).andExpect(status().isNoContent)
        // A maximum puts a card on the wishlist by itself.
        maxPrice(customer, dear, 999).andExpect(status().isNoContent)
        maxPrice(customer, ownedDear, 999).andExpect(status().isNoContent)
        maxPrice(customer, cheap, 2000).andExpect(status().isNoContent)
        add(customer, card(owned).id)
        add(customer, card(ownedDear).id)
        for (id in listOf(free, owned, dear, ownedDear, cheap)) {
            stocked("$word ${card(id).name}", type = "SINGLE", priceCents = 1000, extra = single(id, "NEAR_MINT"))
        }

        val rows = mockMvc.perform(get("/v1/wishlist/cards/for-sale").cookie(customer)).andExpect(status().isOk).json()
            .values().associate { it["product"]["name"].asString().removePrefix("$word ") to it }
        assertEquals("WITHIN", rows.getValue("Free")["match"].asString())
        assertEquals("OWNED", rows.getValue("Owned")["match"].asString())
        assertEquals("ABOVE_MAX", rows.getValue("Dear")["match"].asString())
        assertEquals("ABOVE_MAX", rows.getValue("OwnedDear")["match"].asString())
        assertEquals("WITHIN", rows.getValue("Cheap")["match"].asString())
        assertEquals(2000, rows.getValue("Cheap")["maxPriceCents"].asLong())
        assertEquals(true, rows.getValue("Free")["maxPriceCents"].isNull)
    }

    @Test
    fun `the maximum shows on the card page, can be taken away, and must be an amount`() {
        val customer = loggedInUser()
        val id = uniqueCode()
        importCards(listOf(providerCard(id, name = "Lugia")))
        val cardId = card(id).id

        mockMvc.perform(put("/v1/wishlist/cards/$cardId/max-price").json(mapOf("maxPriceCents" to 500)))
            .andExpect(status().isUnauthorized)
        maxPrice(customer, id, 0).andExpect(status().isBadRequest)
        maxPrice(customer, id, -100).andExpect(status().isBadRequest)
        maxPrice(customer, id, 10_000_001).andExpect(status().isBadRequest)
        mockMvc.perform(put("/v1/wishlist/cards/999999999/max-price").cookie(customer).json(mapOf("maxPriceCents" to 500)))
            .andExpect(status().isNotFound)

        maxPrice(customer, id, 1250).andExpect(status().isNoContent)
        val state = mockMvc.perform(get("/v1/collection/cards/$cardId").cookie(customer)).andExpect(status().isOk).json()
        assertEquals(true, state["onWishlist"].asBoolean())
        assertEquals(1250, state["wishlistMaxPriceCents"].asLong())

        maxPrice(customer, id, null).andExpect(status().isNoContent)
        val cleared = mockMvc.perform(get("/v1/collection/cards/$cardId").cookie(customer)).json()
        assertEquals(true, cleared["onWishlist"].asBoolean())
        assertEquals(true, cleared["wishlistMaxPriceCents"].isNull)
    }

    private fun maxPrice(session: jakarta.servlet.http.Cookie, externalId: String, cents: Long?) =
        mockMvc.perform(put("/v1/wishlist/cards/${card(externalId).id}/max-price").cookie(session).json(mapOf("maxPriceCents" to cents)))

    private fun single(externalId: String, condition: String) = mapOf("cardId" to card(externalId).id, "condition" to condition)
}
