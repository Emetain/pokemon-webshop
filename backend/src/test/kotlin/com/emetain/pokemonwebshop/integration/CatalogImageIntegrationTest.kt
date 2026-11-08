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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import kotlin.test.assertEquals
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
        mockMvc.perform(patch("/v1/account").cookie(admin).json(mapOf("binderSource" to "MOST_EXPENSIVE")))
            .andExpect(status().isOk)

        val body = mockMvc.perform(get("/v1/binder").cookie(admin)).andReturn().response.contentAsString
        val shown = objectMapper.readTree(body)["slots"].map { it["card"]["imageUrl"].asText() }
        assertTrue(url in shown, shown.toString())
    }

    private fun list(query: String) =
        mockMvc.perform(get("/v1/admin/catalog/images/cards?$query").cookie(admin)).andExpect(status().isOk)

    private fun names(query: String): List<String> =
        objectMapper.readTree(list(query).andReturn().response.contentAsString)["items"].map { it["name"].asText() }

    @Test
    fun `the list of cards is for admins only`() {
        mockMvc.perform(get("/v1/admin/catalog/images/cards")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/v1/admin/catalog/images/cards").cookie(loggedInUser())).andExpect(status().isForbidden)
    }

    @Test
    fun `the admin can list the cards without a picture, and an upload takes a card off that list`() {
        val listed = uniqueCode()
        val without = uniqueCode()
        val custom = uniqueCode()
        importCards(
            listOf(
                providerCard(listed, number = "1", name = "With image"),
                providerCard(without, number = "2", name = "Without image").copy(imageSmallUrl = null, imageLargeUrl = null),
                providerCard(custom, number = "3", name = "Uploaded").copy(imageSmallUrl = null, imageLargeUrl = null),
            ),
            code = listed,
        )
        uploadedUrl("/cards/${card(custom).id}")

        assertEquals(listOf("Without image"), names("set=$listed&game=pokemon&image=missing"))
        assertEquals(listOf("Uploaded"), names("set=$listed&game=pokemon&image=custom"))
        assertEquals(3, names("set=$listed&game=pokemon").size)

        list("set=$listed&game=pokemon&image=custom")
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].customImage").value(true))
            .andExpect(jsonPath("$.items[0].setCode").value(listed))
    }

    @Test
    fun `the list sorts by set, by name and by Pokédex number`() {
        val word = uniqueWord()
        val older = uniqueCode()
        val newer = uniqueCode()
        importService.importSet(
            Game.POKEMON,
            providerSet(
                older,
                listOf(
                    providerCard(number = "1", name = "$word Charizard", dexNumbers = setOf(6)),
                    providerCard(number = "2", name = "$word Professor", category = "Trainer"),
                ),
                releaseDate = LocalDate.of(1999, 1, 9),
            ),
        )
        importService.importSet(
            Game.POKEMON,
            providerSet(
                newer,
                listOf(
                    providerCard(number = "1", name = "$word Pikachu", dexNumbers = setOf(25)),
                    providerCard(number = "2", name = "$word Bulbasaur", dexNumbers = setOf(1)),
                ),
                releaseDate = LocalDate.of(2025, 1, 1),
            ),
        )

        assertEquals(
            listOf("$word Pikachu", "$word Bulbasaur", "$word Charizard", "$word Professor"),
            names("q=$word&sort=set"),
        )
        assertEquals(
            listOf("$word Bulbasaur", "$word Charizard", "$word Pikachu", "$word Professor"),
            names("q=$word&sort=name"),
        )
        assertEquals(
            listOf("$word Bulbasaur", "$word Charizard", "$word Pikachu", "$word Professor"),
            names("q=$word&sort=pokedex"),
        )
        list("q=$word&sort=pokedex").andExpect(jsonPath("$.items[0].dexNumbers[0]").value(1))
    }

    @Test
    fun `the admin chooses 25, 50 or 100 cards per page, and anything else means 25`() {
        val many = uniqueCode()
        importCards((1..60).map { providerCard(number = "$it", name = "Card $it") }, code = many)
        val query = "set=$many&game=pokemon"

        list(query)
            .andExpect(jsonPath("$.items.length()").value(25))
            .andExpect(jsonPath("$.totalItems").value(60))
            .andExpect(jsonPath("$.totalPages").value(3))
        list("$query&size=50").andExpect(jsonPath("$.items.length()").value(50)).andExpect(jsonPath("$.totalPages").value(2))
        list("$query&size=100").andExpect(jsonPath("$.items.length()").value(60)).andExpect(jsonPath("$.totalPages").value(1))
        for (size in listOf("33", "1000", "0", "-5")) {
            list("$query&size=$size").andExpect(jsonPath("$.items.length()").value(25))
        }
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
