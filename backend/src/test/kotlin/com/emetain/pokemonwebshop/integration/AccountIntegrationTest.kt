package com.emetain.pokemonwebshop.integration

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AccountIntegrationTest : IntegrationTest() {

    private val address = mapOf(
        "street" to "Pallet Town",
        "houseNumber" to "1a",
        "postcode" to "1234ab",
        "city" to "Amsterdam",
        "country" to "NL",
    )

    private fun createAddress(session: Cookie): Long {
        val response = mockMvc.perform(post("/v1/account/addresses").cookie(session).json(address))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        return objectMapper.readTree(response)["id"].asLong()
    }

    @Test
    fun `account endpoints need a login`() {
        mockMvc.perform(get("/v1/account/addresses")).andExpect(status().isUnauthorized)
        mockMvc.perform(patch("/v1/account").json(mapOf("country" to "BE"))).andExpect(status().isUnauthorized)
    }

    @Test
    fun `the profile and country can be changed`() {
        val session = loggedInUser()

        mockMvc.perform(patch("/v1/account").cookie(session).json(mapOf("displayName" to "Misty", "country" to "BE")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Misty"))
            .andExpect(jsonPath("$.country").value("BE"))

        mockMvc.perform(get("/v1/auth/me").cookie(session))
            .andExpect(jsonPath("$.displayName").value("Misty"))
            .andExpect(jsonPath("$.country").value("BE"))
    }

    @Test
    fun `an unsupported country is refused`() {
        val session = loggedInUser()

        mockMvc.perform(patch("/v1/account").cookie(session).json(mapOf("country" to "US")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("validation_failed"))
    }

    @Test
    fun `the currency starts as euro and can be changed`() {
        val session = loggedInUser()

        mockMvc.perform(get("/v1/auth/me").cookie(session)).andExpect(jsonPath("$.currency").value("EUR"))

        mockMvc.perform(patch("/v1/account").cookie(session).json(mapOf("currency" to "USD")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currency").value("USD"))

        mockMvc.perform(get("/v1/auth/me").cookie(session)).andExpect(jsonPath("$.currency").value("USD"))
    }

    @Test
    fun `an unknown currency is refused`() {
        val session = loggedInUser()

        mockMvc.perform(patch("/v1/account").cookie(session).json(mapOf("currency" to "BTC")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `an address has no name of its own`() {
        val session = loggedInUser()
        createAddress(session)

        mockMvc.perform(get("/v1/account/addresses").cookie(session))
            .andExpect(jsonPath("$[0].street").value("Pallet Town"))
            .andExpect(jsonPath("$[0].name").doesNotExist())
    }

    @Test
    fun `a blank display name is refused`() {
        val session = loggedInUser()

        mockMvc.perform(patch("/v1/account").cookie(session).json(mapOf("displayName" to "   ")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Vul je naam in."))
    }

    @Test
    fun `addresses can be added, changed and removed`() {
        val session = loggedInUser()
        val id = createAddress(session)

        mockMvc.perform(get("/v1/account/addresses").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].postcode").value("1234 AB"))

        mockMvc.perform(
            put("/v1/account/addresses/$id").cookie(session)
                .json(address + mapOf("postcode" to "1000", "country" to "BE", "city" to "Brussel")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.postcode").value("1000"))
            .andExpect(jsonPath("$.city").value("Brussel"))

        mockMvc.perform(delete("/v1/account/addresses/$id").cookie(session)).andExpect(status().isNoContent)
        mockMvc.perform(get("/v1/account/addresses").cookie(session)).andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `an invalid postcode is refused with a dutch message`() {
        val session = loggedInUser()

        mockMvc.perform(post("/v1/account/addresses").cookie(session).json(address + mapOf("postcode" to "12")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Deze postcode klopt niet. Gebruik de notatie 1234 AB."))
    }

    @Test
    fun `an address of someone else can't be read, changed or removed`() {
        val owner = loggedInUser()
        val other = loggedInUser()
        val id = createAddress(owner)

        mockMvc.perform(get("/v1/account/addresses").cookie(other)).andExpect(jsonPath("$.length()").value(0))
        mockMvc.perform(put("/v1/account/addresses/$id").cookie(other).json(address)).andExpect(status().isNotFound)
        mockMvc.perform(delete("/v1/account/addresses/$id").cookie(other)).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/account/addresses").cookie(owner)).andExpect(jsonPath("$.length()").value(1))
    }
}
