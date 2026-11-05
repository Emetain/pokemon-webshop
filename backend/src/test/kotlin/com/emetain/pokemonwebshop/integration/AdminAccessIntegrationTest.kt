package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Role
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AdminAccessIntegrationTest : IntegrationTest() {

    @Test
    fun `a logged out visitor gets 401`() {
        mockMvc.perform(get("/v1/admin/status"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("not_authenticated"))
    }

    @Test
    fun `a customer gets 403`() {
        val session = loggedInUser(Role.CUSTOMER)

        mockMvc.perform(get("/v1/admin/status").cookie(session))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("forbidden"))
            .andExpect(jsonPath("$.message").value("Je hebt geen toegang tot deze pagina."))
    }

    @Test
    fun `an admin gets access`() {
        val session = loggedInUser(Role.ADMIN)

        mockMvc.perform(get("/v1/admin/status").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Testgebruiker"))
    }
}
