package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.ProductImportSource
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.service.ProductImportService
import com.emetain.pokemonwebshop.service.provider.CardmarketProduct
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductImportIntegrationTest : ShopTestSupport() {

    @Autowired
    private lateinit var productImport: ProductImportService

    private val catalogImport get() = importService

    @Autowired
    private lateinit var productRepository: ProductRepository

    private val expansion = Random.nextLong(1_000_000, 9_000_000)
    private val firstId = Random.nextLong(1_000_000_000, 9_000_000_000)

    private fun listed(offset: Long, name: String, category: String = "Pokémon Display") =
        CardmarketProduct(firstId + offset, name, category, expansion)

    /** A set in the card catalog, which the products of [expansion] belong to. */
    private fun importSet(releaseDate: LocalDate = LocalDate.of(2024, 11, 8)): String {
        val code = uniqueCode()
        catalogImport.importSet(Game.POKEMON, providerSet(code, listOf(providerCard()), name = "$word Sparks", releaseDate = releaseDate))
        return code
    }

    @Test
    fun `an import adds every product of a known set as sold out, and twice adds nothing twice`() {
        val code = importSet()
        val list = listOf(
            listed(1, "$word Sparks Booster Box"),
            listed(2, "$word Sparks Booster", "Pokémon Booster"),
            listed(3, "$word Sparks Elite Trainer Box", "Pokémon Elite Trainer Boxes"),
            listed(4, "$word Sparks 6 Booster Box Case"),
            listed(5, "$word Sparks: Pikachu Coin", "Pokémon Coins"),
        )

        val first = productImport.apply(Game.POKEMON, list)
        assertEquals(3, first.created)
        assertEquals(1, first.skippedMarket)
        assertEquals(1, first.skippedType)

        val second = productImport.apply(Game.POKEMON, list)
        assertEquals(0, second.created)
        assertEquals(3, second.unchanged)

        val shop = shop("game=pokemon&set=$code")["results"]
        assertEquals(3, shop["totalItems"].asInt())
        assertTrue(shop["items"].all { !it["canBuy"].asBoolean() && it["setCode"].asText() == code && it["language"].asText() == "EN" })
    }

    @Test
    fun `an import never changes stock or a product the admin edited`() {
        importSet()
        val list = listOf(listed(1, "$word Sparks Booster Box"), listed(2, "$word Sparks Booster Bundle"))
        productImport.apply(Game.POKEMON, list)
        val products = productRepository.findAllByImportSourceAndImportKeyIn(
            ProductImportSource.CARDMARKET,
            list.map { it.id.toString() },
        ).associateBy { it.importKey!!.toLong() }
        val box = products.getValue(firstId + 1)
        val bundle = products.getValue(firstId + 2)

        adjustStock(box.id, 4).andExpect(status().isOk)
        val edited = mockMvc.perform(get("/v1/admin/products/${bundle.id}").cookie(admin)).json()
        val request = objectMapper.convertValue(edited, Map::class.java) + mapOf("name" to "$word Eigen naam", "priceCents" to 3995)
        mockMvc.perform(put("/v1/admin/products/${bundle.id}").cookie(admin).json(request)).andExpect(status().isOk)

        val renamed = list.map { it.copy(name = it.name + " Renamed") }
        val result = productImport.apply(Game.POKEMON, renamed)
        assertEquals(1, result.updated)
        assertEquals(1, result.keptEdited)

        mockMvc.perform(get("/v1/admin/products/${box.id}").cookie(admin))
            .andExpect(jsonPath("$.name").value("$word Sparks Booster Box Renamed"))
            .andExpect(jsonPath("$.stock.onHand").value(4))
        mockMvc.perform(get("/v1/admin/products/${bundle.id}").cookie(admin))
            .andExpect(jsonPath("$.name").value("$word Eigen naam"))
            .andExpect(jsonPath("$.priceCents").value(3995))
    }

    @Test
    fun `products of a set that isn't out yet are pre-orders`() {
        val code = importSet(releaseDate = LocalDate.now().plusMonths(2))
        productImport.apply(Game.POKEMON, listOf(listed(1, "$word Sparks Booster Box")))

        val item = shop("set=$code&game=pokemon")["results"]["items"].single()
        assertTrue(item["preOrder"].asBoolean())
        assertEquals(item["releaseDate"].asText(), item["shipsFrom"].asText())
    }

    @Test
    fun `products of a set out of print stay out`() {
        importSet(releaseDate = LocalDate.of(1999, 1, 9))
        val result = productImport.apply(Game.POKEMON, listOf(listed(1, "$word Sparks Booster Box")))
        assertEquals(0, result.created)
        assertEquals(1, result.skippedOutOfPrint)
    }

    @Test
    fun `products of an expansion without a set in the catalog stay out`() {
        val result = productImport.apply(Game.POKEMON, listOf(listed(1, "$word Unknown Booster Box")))
        assertEquals(0, result.created)
        assertEquals(1, result.skippedNoSet)
    }

    @Test
    fun `the usual products of a set are made in one go, without what the set already has`() {
        val code = importSet()
        mockMvc.perform(get("/v1/admin/product-import/set-templates/pokemon").cookie(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].type").value("BOOSTER_PACK"))
            .andExpect(jsonPath("$[0].usual").value(true))

        fun create(vararg types: String) = mockMvc.perform(
            post("/v1/admin/product-import/set-products").cookie(admin)
                .json(mapOf("game" to "pokemon", "setCode" to code, "language" to "EN", "types" to types.toList())),
        )

        create("BOOSTER_PACK", "ELITE_TRAINER_BOX").andExpect(status().isOk)
            .andExpect(jsonPath("$.created", hasSize<Any>(2)))
            .andExpect(jsonPath("$.created[1].name").value("$word Sparks Elite Trainer Box"))
        create("BOOSTER_PACK", "BOOSTER_BOX").andExpect(status().isOk)
            .andExpect(jsonPath("$.created", hasSize<Any>(1)))
            .andExpect(jsonPath("$.skipped[0]").value("BOOSTER_PACK"))
        create("SINGLE").andExpect(status().isBadRequest)

        assertEquals(3, shop("game=pokemon&set=$code")["results"]["totalItems"].asInt())
    }

    @Test
    fun `a CSV export can be imported again, and changes products and stock`() {
        val product = stocked("$word Export me", quantity = 3)
        val csv = mockMvc.perform(get("/v1/admin/product-import/csv").cookie(admin))
            .andExpect(status().isOk)
            .andReturn().response.getContentAsString(Charsets.UTF_8)
        val header = csv.removePrefix("﻿").lineSequence().first()
        val line = csv.lineSequence().first { it.startsWith("${product["id"]};") }
        assertTrue(line.contains(";99,95;"))

        val unchanged = importCsv("$header\r\n$line\r\n")
        assertEquals(1, unchanged["unchanged"].asInt())
        assertEquals(0, unchanged["stockChanges"].asInt())

        val changed = line.replace(";99,95;", ";89,95;").replace(";3;0;", ";7;0;")
        val updated = importCsv("$header\r\n$changed\r\n")
        assertEquals(1, updated["updated"].asInt())
        assertEquals(1, updated["stockChanges"].asInt())
        mockMvc.perform(get("/v1/admin/products/${product["id"]}").cookie(admin))
            .andExpect(jsonPath("$.priceCents").value(8995))
            .andExpect(jsonPath("$.stock.onHand").value(7))
        mockMvc.perform(get("/v1/admin/products/${product["id"]}/stock/movements").cookie(admin))
            .andExpect(jsonPath("$.items[0].reason").value("CORRECTION"))
            .andExpect(jsonPath("$.items[0].note").value("CSV-import"))
    }

    @Test
    fun `a CSV with only a few columns makes new products, and wrong rows are reported by line`() {
        val result = importCsv(
            "name,game,type,price,status,on_hand\n" +
                "$word New Tin,pokemon,TIN,\"12,95\",ACTIVE,4\n" +
                "$word Wrong,pokemon,NOPE,1,ACTIVE,\n" +
                "$word No game,,TIN,1,ACTIVE,\n",
        )
        assertEquals(1, result["created"].asInt())
        assertEquals(1, result["stockChanges"].asInt())
        assertEquals(listOf(3, 4), result["errors"].map { it["line"].asInt() })

        val tin = shop("")["results"]["items"].single()
        assertEquals(1295, tin["priceCents"].asInt())
        assertTrue(tin["canBuy"].asBoolean())
    }

    @Test
    fun `a CSV with unknown columns is refused`() {
        mockMvc.perform(csvUpload("name;colour\nx;blue\n")).andExpect(status().isBadRequest)
    }

    private fun csvUpload(text: String) = multipart("/v1/admin/product-import/csv")
        .file(MockMultipartFile("file", "producten.csv", "text/csv", text.toByteArray()))
        .cookie(admin)

    private fun importCsv(text: String) = mockMvc.perform(csvUpload(text)).andExpect(status().isOk).json()
}
