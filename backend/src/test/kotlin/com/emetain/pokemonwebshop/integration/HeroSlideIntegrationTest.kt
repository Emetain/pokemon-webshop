package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.repository.HeroSlideRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

class HeroSlideIntegrationTest : ShopTestSupport() {

    @Autowired
    private lateinit var slideRepository: HeroSlideRepository

    @BeforeEach
    fun clearSlides() {
        slideRepository.deleteAll()
    }

    private fun create(title: String, extra: Map<String, Any?> = emptyMap()) =
        mockMvc.perform(post("/v1/admin/hero-slides").cookie(admin).json(mapOf("title" to title) + extra))

    /** The slides from admin. Drops of other tests add slides of their own, which [DropIntegrationTest] covers. */
    private fun showing() = mockMvc.perform(get("/v1/shop/hero-slides")).andExpect(status().isOk).json()
        .filter { it["drop"] == null || it["drop"].isNull }
        .map { it["title"].asText() }

    @Test
    fun `slides show in their order, and only while they are on and within their dates`() {
        val now = Instant.now()
        create("Eerste").andExpect(status().isCreated)
        create("Uit", mapOf("active" to false)).andExpect(status().isCreated)
        create("Later", mapOf("startsAt" to now.plus(1, ChronoUnit.DAYS).toString())).andExpect(status().isCreated)
        create("Voorbij", mapOf("endsAt" to now.minus(1, ChronoUnit.HOURS).toString())).andExpect(status().isCreated)
        val last = create("Laatste", mapOf("buttonLabel" to "Kijk", "linkUrl" to "/pokemon", "game" to "lorcana"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.showing").value(true))
            .json()

        assertEquals(listOf("Eerste", "Laatste"), showing())

        mockMvc.perform(post("/v1/admin/hero-slides/${last["id"]}/move?direction=up").cookie(admin)).andExpect(status().isOk)
        mockMvc.perform(post("/v1/admin/hero-slides/${last["id"]}/move?direction=up").cookie(admin)).andExpect(status().isOk)
        mockMvc.perform(post("/v1/admin/hero-slides/${last["id"]}/move?direction=up").cookie(admin)).andExpect(status().isOk)
        mockMvc.perform(post("/v1/admin/hero-slides/${last["id"]}/move?direction=up").cookie(admin))
            .andExpect(jsonPath("$[0].title").value("Laatste"))
        assertEquals(listOf("Laatste", "Eerste"), showing())

        mockMvc.perform(get("/v1/shop/hero-slides"))
            .andExpect(jsonPath("$[0].game").value("lorcana"))
            .andExpect(jsonPath("$[0].linkUrl").value("/pokemon"))
    }

    @Test
    fun `a slide can be changed, get an image and be removed`() {
        val id = create("Oud").andExpect(status().isCreated).json()["id"].asLong()
        mockMvc.perform(put("/v1/admin/hero-slides/$id").cookie(admin).json(mapOf("title" to "Nieuw", "text" to "Tekst")))
            .andExpect(jsonPath("$.title").value("Nieuw"))
        val image = mockMvc.perform(
            multipart("/v1/admin/hero-slides/$id/image").file(MockMultipartFile("file", "a.png", "image/png", png)).cookie(admin),
        ).andExpect(status().isOk).json()["imageUrl"].asText()
        mockMvc.perform(get(image.removePrefix("/api"))).andExpect(status().isOk)

        mockMvc.perform(delete("/v1/admin/hero-slides/$id").cookie(admin)).andExpect(status().isNoContent)
        mockMvc.perform(get(image.removePrefix("/api"))).andExpect(status().isNotFound)
        assertEquals(emptyList(), showing())
    }

    @Test
    fun `links can only go to the shop or to https, with a button text`() {
        create("x", mapOf("buttonLabel" to "Klik", "linkUrl" to "javascript:alert(1)")).andExpect(status().isBadRequest)
        create("x", mapOf("buttonLabel" to "Klik", "linkUrl" to "//evil.example")).andExpect(status().isBadRequest)
        create("x", mapOf("buttonLabel" to "Klik", "linkUrl" to "http://example.com")).andExpect(status().isBadRequest)
        create("x", mapOf("linkUrl" to "/pokemon")).andExpect(status().isBadRequest)
        create("x", mapOf("buttonLabel" to "Klik", "linkUrl" to "https://example.com/a")).andExpect(status().isCreated)
        create("").andExpect(status().isBadRequest)
        create(
            "x",
            mapOf("startsAt" to "2026-12-01T00:00:00Z", "endsAt" to "2026-11-01T00:00:00Z"),
        ).andExpect(status().isBadRequest)
    }
}
