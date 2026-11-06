package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.ExchangeRate
import com.emetain.pokemonwebshop.repository.ExchangeRateRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SharedCollectionIntegrationTest : CollectionTestSupport() {

    @Autowired
    private lateinit var exchangeRateRepository: ExchangeRateRepository

    private fun slugOf(body: String): String = objectMapper.readTree(body)["collectionShareSlug"].asText()

    private fun userWithFullName(): jakarta.servlet.http.Cookie {
        val session = loggedInUser()
        mockMvc.perform(patch("/v1/account").cookie(session).json(mapOf("displayName" to "Ash Ketchum van Pallet")))
            .andExpect(status().isOk)
        return session
    }

    private fun share(session: jakarta.servlet.http.Cookie): String =
        slugOf(mockMvc.perform(put("/v1/collection/share").cookie(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `a collection is private until its owner shares it`() {
        mockMvc.perform(get("/v1/auth/me").cookie(loggedInUser()))
            .andExpect(jsonPath("$.collectionShareSlug").doesNotExist())
    }

    @Test
    fun `anyone with the link sees the first name, progress and value, but nothing else about the owner`() {
        val session = userWithFullName()
        val raichu = uniqueCode()
        importCards(listOf(providerCard(raichu, name = "Raichu", prices = listOf(providerPrice(trend = 300)))))
        add(session, card(raichu).id)

        val body = mockMvc.perform(get("/v1/shared-collections/${share(session)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ownerName").value("Ash"))
            .andExpect(jsonPath("$.collection.value.cents").value(300))
            .andExpect(jsonPath("$.collection.copies").value(1))
            .andExpect(jsonPath("$.collection.mostValuable[0].name").value("Raichu"))
            .andReturn().response.contentAsString

        assertTrue("Ketchum" !in body)
        assertTrue("@example.com" !in body)
    }

    @Test
    fun `the value is shown in the currency of the viewer`() {
        exchangeRateRepository.save(ExchangeRate(Currency.USD, BigDecimal("1.25"), LocalDate.of(2026, 11, 5)))
        val session = userWithFullName()
        val raichu = uniqueCode()
        importCards(listOf(providerCard(raichu, prices = listOf(providerPrice(trend = 1000)))))
        add(session, card(raichu).id)
        val slug = share(session)

        val viewer = loggedInUser()
        mockMvc.perform(patch("/v1/account").cookie(viewer).json(mapOf("currency" to "USD"))).andExpect(status().isOk)

        mockMvc.perform(get("/v1/shared-collections/$slug").cookie(viewer))
            .andExpect(jsonPath("$.collection.value.cents").value(1250))
            .andExpect(jsonPath("$.collection.value.currency").value("USD"))
    }

    @Test
    fun `sharing twice keeps the same link`() {
        val session = loggedInUser()

        assertEquals(share(session), share(session))
    }

    @Test
    fun `stopping closes the link for good, and sharing again gives a new one`() {
        val session = loggedInUser()
        val first = share(session)

        mockMvc.perform(delete("/v1/collection/share").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.collectionShareSlug").doesNotExist())

        mockMvc.perform(get("/v1/shared-collections/$first")).andExpect(status().isNotFound)
        assertNotEquals(first, share(session))
    }

    @Test
    fun `a link that doesn't exist is not found`() {
        mockMvc.perform(get("/v1/shared-collections/does-not-exist")).andExpect(status().isNotFound)
    }
}
