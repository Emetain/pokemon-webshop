package com.emetain.pokemonwebshop.integration

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Limits and access, called straight at the API the way a bot would: without the waiting room, with someone else's
 * token, past the limits, from a second account or with a different spelling of the same address and email.
 */
class DropLimitsIntegrationTest : DropTestSupport() {

    @Test
    fun `the drop cart and checkout need your own valid access token`() {
        val product = stocked(quantity = 5)
        val drop = createDrop(listOf(product["id"].asLong() to 5))
        val early = buyer()
        join(drop, early).andExpect(status().isOk)
        setQuantity(drop, early, drop.dropProductIds[0], 1).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("drop_no_access"))

        startNow(drop)
        val waiting = buyer()
        setSpeed(drop, 1)
        join(drop, waiting).andExpect(status().isOk)
        setQuantity(drop, waiting, drop.dropProductIds[0], 1).andExpect(status().isForbidden)
        checkout(drop, waiting).andExpect(status().isForbidden)

        // Someone else's token doesn't work for you.
        val admitted = admittedBuyer(drop)
        val thief = buyer().copy(access = admitted.access)
        setQuantity(drop, thief, drop.dropProductIds[0], 1).andExpect(status().isForbidden)
        mockMvc.perform(get("/v1/drops/${drop.slug}/cart").with(fromRandomIp()).as_(thief)).andExpect(status().isForbidden)

        // Logged out, with a token: an account is required.
        mockMvc.perform(put("/v1/drops/${drop.slug}/cart/${drop.dropProductIds[0]}").cookie(admitted.access!!).json(mapOf("quantity" to 1)))
            .andExpect(status().isUnauthorized)

        // A product from another drop isn't in this drop cart.
        val other = createDrop(listOf(stocked(name = "$word Ander", quantity = 2)["id"].asLong() to 2))
        setQuantity(drop, admitted, other.dropProductIds[0], 1).andExpect(status().isNotFound)
        setQuantity(drop, admitted, drop.dropProductIds[0], 1).andExpect(status().isOk)
    }

    @Test
    fun `a guest can't join a drop that requires an account, and a bot filling the hidden field is turned away`() {
        val product = stocked(quantity = 5)
        val drop = createDrop(listOf(product["id"].asLong() to 5))
        join(drop, null).andExpect(status().isUnauthorized)
        join(drop, buyer(), mapOf("website" to "https://spam.example")).andExpect(status().isBadRequest)
        mockMvc.perform(post("/v1/drops/${drop.slug}/reminders").with(fromRandomIp()).json(mapOf("email" to "a@example.com", "website" to "x")))
            .andExpect(status().isBadRequest)

        startNow(drop)
        val buyer = admittedBuyer(drop)
        setQuantity(drop, buyer, drop.dropProductIds[0], 1).andExpect(status().isOk)
        checkout(drop, buyer, mapOf("website" to "x")).andExpect(status().isBadRequest)
        assertEquals(0, dropProduct(drop.id, drop.dropProductIds[0]).reserved)
    }

    @Test
    fun `limits per product and per drop are checked when something goes in the drop cart`() {
        val first = stocked(name = "$word Eerste", quantity = 10)
        val second = stocked(name = "$word Tweede", quantity = 10)
        val drop = createDrop(listOf(first["id"].asLong() to 10, second["id"].asLong() to 10), maxPerCustomer = 3, productMax = 2)
        startNow(drop)
        val buyer = admittedBuyer(drop)

        setQuantity(drop, buyer, drop.dropProductIds[0], 3).andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("drop_limit"))
        setQuantity(drop, buyer, drop.dropProductIds[0], 2).andExpect(status().isOk)
        setQuantity(drop, buyer, drop.dropProductIds[1], 2).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Je kunt maximaal 3 producten kopen in deze drop."))
        val cart = setQuantity(drop, buyer, drop.dropProductIds[1], 1).andExpect(status().isOk).json()
        assertEquals(3, cart["cart"]["itemCount"].asInt())
        assertEquals(1, cart["limits"][1]["room"].asInt())

        // More than is left never goes in.
        val scarce = createDrop(listOf(stocked(name = "$word Schaars", quantity = 1)["id"].asLong() to 1))
        startNow(scarce)
        val other = admittedBuyer(scarce)
        setQuantity(scarce, other, scarce.dropProductIds[0], 2).andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("out_of_stock"))
    }

    @Test
    fun `paid orders count, so after buying the limit nothing more goes in, also after getting back in line`() {
        val product = stocked(quantity = 10)
        val drop = createDrop(listOf(product["id"].asLong() to 10), productMax = 1)
        startNow(drop)
        val buyer = admittedBuyer(drop)
        buy(drop, buyer, drop.dropProductIds[0])

        setQuantity(drop, buyer, drop.dropProductIds[0], 1).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Je kunt van ${product["name"].asText()} maximaal 1 kopen in deze drop. Je hebt er al 1 besteld."))

        changeEntry(buyer, drop) { it.accessExpiresAt = java.time.Instant.now().minusSeconds(1) }
        join(drop, buyer).andExpect(status().isOk)
        nextBatch(drop)
        assertEquals("ADMITTED", ping(drop, buyer)["state"].asText())
        setQuantity(drop, buyer, drop.dropProductIds[0], 1).andExpect(status().isConflict)
    }

    @Test
    fun `a second account with the same address, written differently, can't buy past the limit`() {
        val product = stocked(quantity = 10)
        val drop = createDrop(listOf(product["id"].asLong() to 10), productMax = 1)
        startNow(drop)
        buy(drop, admittedBuyer(drop), drop.dropProductIds[0])

        val second = admittedBuyer(drop)
        setQuantity(drop, second, drop.dropProductIds[0], 1).andExpect(status().isOk)
        val sameAddress = address + mapOf("street" to "dorpsstraat ", "houseNumber" to "12 A", "postcode" to "1421 AB")
        val refused = checkout(drop, second, mapOf("address" to sameAddress)).andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("drop_limit")).json()
        assertContains(refused["message"].asText(), "al 1 besteld")

        // Another address in the same street is someone else.
        checkout(drop, second, mapOf("address" to address + mapOf("houseNumber" to "14"))).andExpect(status().isCreated)
    }

    @Test
    fun `in a drop without account, the same Gmail with dots or a tag counts as one person`() {
        val product = stocked(quantity = 10)
        val drop = createDrop(listOf(product["id"].asLong() to 10), productMax = 1, extra = mapOf("requiresAccount" to false))
        startNow(drop)
        setSpeed(drop, 6000)

        fun guest(): Buyer {
            val joined = join(drop, null).andExpect(status().isOk).andReturn().response
            val visitor = requireNotNull(joined.getCookie("drop_visitor")) { "No visitor cookie" }
            val guest = Buyer(visitor, "")
            nextBatch(drop)
            assertEquals("ADMITTED", ping(drop, guest)["state"].asText())
            return guest
        }

        val first = guest()
        val local = word.lowercase()
        buy(drop, first, drop.dropProductIds[0], extra = mapOf("name" to "Ash", "email" to "$local.ketchum+drop@gmail.com"))

        val second = guest()
        setQuantity(drop, second, drop.dropProductIds[0], 1).andExpect(status().isOk)
        val otherHouse = address + mapOf("houseNumber" to "99")
        checkout(drop, second, mapOf("name" to "Ash", "email" to "${local}ketchum@googlemail.com", "address" to otherHouse))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("drop_limit"))
    }

    @Test
    fun `two checkouts of the same person at the same moment pass the limit once`() {
        val product = stocked(quantity = 10)
        val drop = createDrop(listOf(product["id"].asLong() to 10), productMax = 1)
        startNow(drop)
        val buyer = admittedBuyer(drop)
        setQuantity(drop, buyer, drop.dropProductIds[0], 1).andExpect(status().isOk)

        val results = java.util.concurrent.Executors.newFixedThreadPool(2).let { pool ->
            val start = java.util.concurrent.CountDownLatch(1)
            val futures = (1..2).map {
                pool.submit<Int> {
                    start.await()
                    checkout(drop, buyer).andReturn().response.status
                }
            }
            start.countDown()
            futures.map { it.get() }.also { pool.shutdown() }
        }
        val held = dropProduct(drop.id, drop.dropProductIds[0]).reserved
        assertEquals(1, held, "one checkout holds the stock: $results")
        assertEquals(1, orderRepository.findAll().count { it.drop?.id == drop.id && it.status.name == "PENDING_PAYMENT" })
    }

    @Test
    fun `the normal cart can't buy what was allocated to a drop`() {
        val product = stocked(quantity = 3)
        createDrop(listOf(product["id"].asLong() to 3))
        mockMvc.perform(post("/v1/cart/items").json(mapOf("productId" to product["id"].asLong(), "quantity" to 1)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("out_of_stock"))
    }
}
