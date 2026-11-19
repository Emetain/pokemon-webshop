package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Role
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** SHOP_ACCESS=unlisted: the site is online without the preview login, but only admins can order. */
@TestPropertySource(
    properties = [
        "app.shop-access=unlisted",
        "spring.datasource.url=jdbc:h2:mem:unlisted-shop;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000",
    ],
)
class UnlistedShopIntegrationTest : IntegrationTest() {

    private val body = mapOf(
        "address" to mapOf("street" to "Dorpsstraat", "houseNumber" to "12a", "postcode" to "1421ab", "city" to "Uithoorn", "country" to "NL"),
        "name" to "Ash Ketchum",
        "email" to "ash-unlisted@example.com",
        "acceptTerms" to true,
    )

    @Test
    fun `visitors and customers can look but not order, admins can`() {
        mockMvc.perform(get("/v1/checkout/options")).andExpect(status().isOk).andExpect(jsonPath("$.canOrder").value(false))
        mockMvc.perform(get("/v1/checkout/options").cookie(loggedInUser()))
            .andExpect(jsonPath("$.canOrder").value(false))
        mockMvc.perform(get("/v1/checkout/options").cookie(loggedInUser(Role.ADMIN)))
            .andExpect(jsonPath("$.canOrder").value(true))

        mockMvc.perform(post("/v1/checkout").with(fromRandomIp()).json(body))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("shop_not_open"))
        mockMvc.perform(post("/v1/checkout").with(fromRandomIp()).cookie(loggedInUser()).json(body))
            .andExpect(status().isForbidden)
        // An admin gets past the gate, to the next check (an empty cart).
        mockMvc.perform(post("/v1/checkout").with(fromRandomIp()).cookie(loggedInUser(Role.ADMIN)).json(body))
            .andExpect(jsonPath("$.error").value(not("shop_not_open")))
    }
}
