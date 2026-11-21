package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Role
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** The backend's messages in the visitor's language (2026-11-21): Dutch by default, English when the site asks. */
class ErrorLanguageIntegrationTest : IntegrationTest() {

    private fun wrongLogin(language: String?) = mockMvc.perform(
        post("/v1/auth/login").with(fromRandomIp())
            .apply { if (language != null) header(HttpHeaders.ACCEPT_LANGUAGE, language) }
            .json(mapOf("email" to uniqueEmail(), "password" to "verkeerd123")),
    )

    @Test
    fun `without a language, or in Dutch, messages stay Dutch`() {
        wrongLogin(null).andExpect(jsonPath("$.message").value("E-mailadres of wachtwoord klopt niet."))
        wrongLogin("nl").andExpect(jsonPath("$.message").value("E-mailadres of wachtwoord klopt niet."))
    }

    @Test
    fun `in English the message is English, and the error code stays the same`() {
        wrongLogin("en").andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("invalid_credentials"))
            .andExpect(jsonPath("$.message").value("Email address or password isn't right."))
        mockMvc.perform(get("/v1/collection").header(HttpHeaders.ACCEPT_LANGUAGE, "en-GB,en;q=0.9"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Log in to continue."))
    }

    @Test
    fun `a check on a form comes back in English too`() {
        mockMvc.perform(
            post("/v1/auth/register").with(fromRandomIp()).header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                .json(mapOf("email" to uniqueEmail(), "password" to "kort", "passwordConfirmation" to "kort", "displayName" to "Ash")),
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Choose a password of at least 8 characters."))
    }

    @Test
    fun `the admin area stays Dutch, like its pages`() {
        val customer = loggedInUser(Role.CUSTOMER)
        mockMvc.perform(get("/v1/admin/status").cookie(customer).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("Je hebt geen toegang tot deze pagina."))
    }
}
