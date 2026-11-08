package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Role
import com.fasterxml.jackson.databind.JsonNode
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Makes products and stock through the admin API, like the admin pages do. */
abstract class ShopTestSupport : CollectionTestSupport() {

    protected lateinit var admin: Cookie

    /** In every product name of a test, so a search only finds that test's products. */
    protected val word = uniqueWord()

    protected val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    @BeforeEach
    fun loginAdmin() {
        admin = loggedInUser(Role.ADMIN)
    }

    protected fun ResultActions.json(): JsonNode = objectMapper.readTree(andReturn().response.contentAsString)

    protected fun createProduct(
        name: String = "$word Booster Box",
        type: String = "BOOSTER_BOX",
        game: String? = "pokemon",
        priceCents: Long? = 9995,
        status: String = "ACTIVE",
        extra: Map<String, Any?> = emptyMap(),
    ): JsonNode = mockMvc.perform(
        post("/v1/admin/products").cookie(admin).json(
            mapOf(
                "game" to game,
                "type" to type,
                "name" to name,
                "priceCents" to priceCents,
                "status" to status,
                "language" to "EN",
            ) + extra,
        ),
    ).andExpect(status().isCreated).json()

    protected fun adjustStock(productId: Long, change: Int, reason: String = "PURCHASE", note: String? = null): ResultActions =
        mockMvc.perform(
            post("/v1/admin/products/$productId/stock/adjustments").cookie(admin)
                .json(mapOf("change" to change, "reason" to reason, "note" to note)),
        )

    protected fun stocked(name: String = "$word Booster Box", quantity: Int = 5, extra: Map<String, Any?> = emptyMap(), type: String = "BOOSTER_BOX", priceCents: Long? = 9995): JsonNode {
        val product = createProduct(name = name, extra = extra, type = type, priceCents = priceCents)
        adjustStock(product["id"].asLong(), quantity).andExpect(status().isOk)
        return product
    }

    protected fun shop(query: String, session: Cookie? = null): JsonNode {
        val request = get("/v1/products?q=$word&$query")
        return mockMvc.perform(if (session != null) request.cookie(session) else request).andExpect(status().isOk).json()
    }

    protected fun names(query: String): List<String> = shop(query)["results"]["items"].map { it["name"].asText() }

    protected fun uploadImage(productId: Long, bytes: ByteArray = png): ResultActions =
        mockMvc.perform(
            multipart("/v1/admin/products/$productId/images")
                .file(MockMultipartFile("file", "foto.png", "image/png", bytes))
                .cookie(admin),
        )
}
