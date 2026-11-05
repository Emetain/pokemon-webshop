package com.emetain.pokemonwebshop.integration

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ErrorHandlingIntegrationTest : IntegrationTest() {

    @Test
    fun `validation errors use the error format`() {
        mockMvc.perform(
            post("/v1/auth/register").with(fromRandomIp())
                .json(mapOf("email" to "geen-email", "password" to "geheim123", "displayName" to "Ash")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("validation_failed"))
            .andExpect(jsonPath("$.message").value("Dit e-mailadres klopt niet."))
    }

    @Test
    fun `invalid json uses the error format`() {
        mockMvc.perform(
            post("/v1/auth/login").with(fromRandomIp()).contentType(MediaType.APPLICATION_JSON).content("{niet json"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("bad_request"))
    }

    @Test
    fun `an unknown path uses the error format`() {
        mockMvc.perform(get("/v1/bestaat-niet"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("not_found"))
    }

    @Test
    fun `a wrong http method uses the error format`() {
        mockMvc.perform(get("/v1/auth/login"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.error").value("method_not_allowed"))
    }

    @Test
    fun `too many login attempts from one ip are blocked`() {
        val sameIp: RequestPostProcessor = fromRandomIp()
        val attempt = {
            mockMvc.perform(
                post("/v1/auth/login").with(sameIp).json(mapOf("email" to "ash@example.com", "password" to "fout")),
            )
        }

        repeat(5) { attempt().andExpect(status().isUnauthorized) }

        attempt()
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error").value("rate_limited"))
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
    }

    @Test
    fun `too many registrations from one ip are blocked`() {
        val sameIp: RequestPostProcessor = fromRandomIp()
        val attempt = {
            mockMvc.perform(
                post("/v1/auth/register").with(sameIp)
                    .json(mapOf("email" to uniqueEmail(), "password" to password, "displayName" to "Ash")),
            )
        }

        repeat(5) { attempt().andExpect(status().isCreated) }

        attempt().andExpect(status().isTooManyRequests)
    }
}
