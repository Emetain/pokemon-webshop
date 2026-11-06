package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.Role
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CatalogImageIntegrationTest : CollectionTestSupport() {

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    private lateinit var admin: Cookie
    private val externalId = uniqueCode()
    private val code = uniqueCode()
    private val providerCards get() = listOf(providerCard(externalId, name = "Mew", number = "1"))

    @BeforeEach
    fun importCard() {
        admin = loggedInUser(Role.ADMIN)
        importCards(providerCards, code = code)
    }

    private val cardId get() = card(externalId).id

    private fun file(bytes: ByteArray, name: String = "kaart.png", type: String = "image/png") =
        MockMultipartFile("file", name, type, bytes)

    private fun upload(session: Cookie, path: String, bytes: ByteArray = png) =
        mockMvc.perform(multipart("/v1/admin/catalog/images$path").file(file(bytes)).cookie(session))

    private fun uploadedUrl(path: String, bytes: ByteArray = png): String {
        val response = upload(admin, path, bytes).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readTree(response)["imageUrl"].asText()
    }

    /** The context path isn't part of a MockMvc request. */
    private fun mediaPath(url: String) = url.removePrefix("/api")

    @Test
    fun `only an admin can change pictures`() {
        val customer = loggedInUser()

        mockMvc.perform(multipart("/v1/admin/catalog/images/cards/$cardId").file(file(png)))
            .andExpect(status().isUnauthorized)
        upload(customer, "/cards/$cardId").andExpect(status().isForbidden)
        upload(customer, "/sets/pokemon/$code").andExpect(status().isForbidden)
        mockMvc.perform(delete("/v1/admin/catalog/images/cards/$cardId").cookie(customer)).andExpect(status().isForbidden)
        mockMvc.perform(delete("/v1/admin/catalog/images/sets/pokemon/$code").cookie(customer)).andExpect(status().isForbidden)
    }

    @Test
    fun `an uploaded picture shows everywhere and can be fetched by anyone`() {
        val url = uploadedUrl("/cards/$cardId")

        assertTrue(url.startsWith("/api/v1/media/"), url)
        mockMvc.perform(get("/v1/catalog/pokemon/sets/$code/cards"))
            .andExpect(jsonPath("$.items[0].imageUrl").value(url))
            .andExpect(jsonPath("$.items[0].customImage").value(true))
        mockMvc.perform(get("/v1/catalog/cards/$cardId"))
            .andExpect(jsonPath("$.imageSmallUrl").value(url))
            .andExpect(jsonPath("$.imageLargeUrl").value(url))

        mockMvc.perform(get(mediaPath(url)))
            .andExpect(status().isOk)
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(png))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().exists(HttpHeaders.CACHE_CONTROL))
    }

    @Test
    fun `the file type comes from the file itself and not from its name`() {
        val response = mockMvc.perform(
            multipart("/v1/admin/catalog/images/cards/$cardId")
                .file(file(jpeg, name = "kaart.png", type = "image/png"))
                .cookie(admin),
        ).andExpect(status().isOk).andReturn().response.contentAsString

        val url = objectMapper.readTree(response)["imageUrl"].asText()

        assertTrue(url.endsWith(".jpg"), url)
        mockMvc.perform(get(mediaPath(url))).andExpect(content().contentType("image/jpeg"))
    }

    @Test
    fun `anything that isn't an image is refused, whatever it's called`() {
        val svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".toByteArray()

        mockMvc.perform(
            multipart("/v1/admin/catalog/images/cards/$cardId")
                .file(file(svg, name = "kaart.png", type = "image/png"))
                .cookie(admin),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("validation_failed"))

        mockMvc.perform(get("/v1/catalog/cards/$cardId"))
            .andExpect(jsonPath("$.imageSmallUrl").value("https://example.com/$externalId/low.webp"))
    }

    @Test
    fun `a sync leaves an uploaded picture alone, and keeps the picture of the source underneath`() {
        val url = uploadedUrl("/cards/$cardId")

        importService.importSet(
            Game.POKEMON,
            providerSet(code, listOf(providerCards.single().copy(imageSmallUrl = "https://example.com/new.webp"))),
        )

        mockMvc.perform(get("/v1/catalog/cards/$cardId")).andExpect(jsonPath("$.imageSmallUrl").value(url))

        mockMvc.perform(delete("/v1/admin/catalog/images/cards/$cardId").cookie(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.imageUrl").value("https://example.com/new.webp"))
            .andExpect(jsonPath("$.custom").value(false))
    }

    @Test
    fun `resetting removes the file, and a new picture replaces the old file`() {
        val first = uploadedUrl("/cards/$cardId")
        val second = uploadedUrl("/cards/$cardId")

        assertNotEquals(first, second)
        mockMvc.perform(get(mediaPath(first))).andExpect(status().isNotFound)
        mockMvc.perform(get(mediaPath(second))).andExpect(status().isOk)

        mockMvc.perform(delete("/v1/admin/catalog/images/cards/$cardId").cookie(admin)).andExpect(status().isOk)

        mockMvc.perform(get(mediaPath(second))).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/catalog/pokemon/sets/$code/cards"))
            .andExpect(jsonPath("$.items[0].customImage").value(false))
    }

    @Test
    fun `a set gets a new logo and can go back`() {
        val url = uploadedUrl("/sets/pokemon/$code")

        mockMvc.perform(get("/v1/catalog/pokemon/sets/$code"))
            .andExpect(jsonPath("$.set.logoUrl").value(url))
            .andExpect(jsonPath("$.set.customLogo").value(true))

        importService.importSet(Game.POKEMON, providerSet(code, providerCards))
        mockMvc.perform(get("/v1/catalog/pokemon/sets/$code")).andExpect(jsonPath("$.set.logoUrl").value(url))

        mockMvc.perform(delete("/v1/admin/catalog/images/sets/pokemon/$code").cookie(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.imageUrl").value("https://example.com/$code.webp"))
    }

    @Test
    fun `a card without an image of its own can get one, and then counts for the binder`() {
        val noImage = uniqueCode()
        val expensive = listOf(providerPrice(trend = 3_500_000_000))
        importCards(listOf(providerCard(noImage, prices = expensive).copy(imageSmallUrl = null)), game = Game.MTG)

        val url = uploadedUrl("/cards/${card(noImage, Game.MTG).id}")

        val body = mockMvc.perform(get("/v1/binder")).andReturn().response.contentAsString
        val shown = objectMapper.readTree(body)["slots"].map { it["card"]["imageUrl"].asText() }
        assertTrue(url in shown, shown.toString())
    }

    @Test
    fun `an unknown card, set or game is not found`() {
        upload(admin, "/cards/999999999").andExpect(status().isNotFound)
        upload(admin, "/sets/pokemon/nope").andExpect(status().isNotFound)
        upload(admin, "/sets/poker/$code").andExpect(status().isNotFound)
    }

    @Test
    fun `the media endpoint only serves files we stored`() {
        for (name in listOf("nope.png", "..%2F..%2Fetc%2Fpasswd", "abc.svg", "a".repeat(32) + ".exe", "a".repeat(32) + ".png")) {
            mockMvc.perform(get("/v1/media/$name")).andExpect(status().isNotFound)
        }
    }
}
