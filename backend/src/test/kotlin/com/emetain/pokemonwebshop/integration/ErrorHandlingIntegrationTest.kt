package com.emetain.pokemonwebshop.integration

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
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
                .json(mapOf("email" to "geen-email", "password" to "geheim123", "passwordConfirmation" to "geheim123", "displayName" to "Ash")),
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
                    .json(mapOf("email" to uniqueEmail(), "password" to password, "passwordConfirmation" to password, "displayName" to "Ash")),
            )
        }

        repeat(5) { attempt().andExpect(status().isCreated) }

        attempt().andExpect(status().isTooManyRequests)
    }

    @Test
    fun `a json body above the limit is refused before it is read`() {
        val padding = "a".repeat(300 * 1024)

        mockMvc.perform(
            post("/v1/auth/login").with(fromRandomIp())
                .json(mapOf("email" to "ash@example.com", "password" to "fout", "padding" to padding)),
        )
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.error").value("request_too_large"))
            .andExpect(jsonPath("$.message").value("Dit verzoek is te groot."))
    }

    @Test
    fun `a json body below the limit is read as usual`() {
        val padding = "a".repeat(200 * 1024)

        mockMvc.perform(
            post("/v1/auth/login").with(fromRandomIp())
                .json(mapOf("email" to "ash@example.com", "password" to "fout", "padding" to padding)),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `uploads keep their own limit`() {
        val picture = MockMultipartFile("file", "foto.png", "image/png", ByteArray(1024 * 1024))

        // A visitor: the upload limit lets the request through, and the login check refuses it.
        mockMvc.perform(multipart("/v1/admin/products/1/images").file(picture).with(fromRandomIp()))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a query value of the wrong type gets a dutch answer without internals`() {
        // Bound to a request object.
        mockMvc.perform(get("/v1/products?page=abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("bad_request"))
            .andExpect(jsonPath("$.message").value("Dit verzoek klopt niet. Controleer de ingevulde gegevens."))
        // A single request parameter.
        mockMvc.perform(get("/v1/products/new-arrivals?limit=veel"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("bad_request"))
            .andExpect(jsonPath("$.message").value("Dit verzoek klopt niet. Controleer de ingevulde gegevens."))
        // A path variable.
        mockMvc.perform(get("/v1/catalog/cards/abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("bad_request"))
    }
}
