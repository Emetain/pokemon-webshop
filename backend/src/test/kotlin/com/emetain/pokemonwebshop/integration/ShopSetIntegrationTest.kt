package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.service.ProductSlugs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which sets the shop shows, and how it orders their products. The test profile counts sets from 2000 as in print. */
class ShopSetIntegrationTest : ShopTestSupport() {

    @Autowired
    private lateinit var properties: AppProperties

    private fun set(releaseDate: LocalDate, series: String = "$word Series", code: String = uniqueCode()): String {
        importService.importSet(Game.POKEMON, providerSet(code, listOf(providerCard()), series = series, releaseDate = releaseDate))
        return code
    }

    private fun inSet(code: String, name: String, type: String = "BOOSTER_BOX") =
        createProduct(name = name, type = type, extra = mapOf("setCode" to code))

    private fun series(): List<Pair<String, Int>> =
        mockMvc.perform(get("/v1/shop/pokemon/series")).andExpect(status().isOk).json()
            .filter { it["name"].asText().startsWith(word) }
            .map { it["name"].asText() to it["productCount"].asInt() }

    @Test
    fun `the settings name games by their enum and read dates and set codes`() {
        assertEquals(LocalDate.of(2000, 1, 1), properties.shop.inPrintFrom[Game.ONE_PIECE])
        assertEquals(listOf("popular-a", "popular-b"), properties.shop.popularSets[Game.POKEMON])
        assertEquals("OP-17", properties.shop.popularSets[Game.ONE_PIECE]?.first())
    }

    @Test
    fun `a set out of print leaves the shop, unless something of it can be bought`() {
        val inPrint = set(LocalDate.of(2025, 1, 17), series = "$word New")
        val oldWithStock = set(LocalDate.of(1999, 1, 9), series = "$word Old")
        val oldSoldOut = set(LocalDate.of(1999, 6, 16), series = "$word Gone")
        inSet(inPrint, "$word New box")
        val hidden = inSet(oldWithStock, "$word Old sold out")
        val stocked = inSet(oldWithStock, "$word Old in stock")
        adjustStock(stocked["id"].asLong(), 1)
        inSet(oldSoldOut, "$word Gone box")

        assertEquals(setOf("$word New box", "$word Old in stock"), names("game=pokemon").toSet())
        assertEquals(listOf("$word Old in stock"), names("game=pokemon&set=$oldWithStock"))
        assertEquals(emptyList(), names("game=pokemon&set=$oldSoldOut"))
        assertEquals(listOf("$word New" to 1, "$word Old" to 1), series())
        mockMvc.perform(get("/v1/shop/pokemon/series/${ProductSlugs.of("$word Gone")}")).andExpect(status().isNotFound)

        // Not in lists, but a link or a wishlist still leads to it.
        mockMvc.perform(get("/v1/products/${hidden["slug"].asText()}")).andExpect(status().isOk)
        assertFalse(cardSetRepository.findByGameAndCode(Game.POKEMON, oldSoldOut)!!.inPrint)
        assertTrue(cardSetRepository.findByGameAndCode(Game.POKEMON, inPrint)!!.inPrint)
    }

    @Test
    fun `newest goes by the release date of the set and keeps a set together, booster boxes first`() {
        val older = set(LocalDate.of(2023, 3, 31))
        val newest = set(LocalDate.of(2025, 5, 30))
        val middle = set(LocalDate.of(2024, 11, 8))
        // Made in another order than they came out, the way an import makes them.
        inSet(newest, "$word Newest pack", type = "BOOSTER_PACK")
        inSet(older, "$word Older box")
        inSet(middle, "$word Middle box")
        inSet(newest, "$word Newest box")
        createProduct(name = "$word Without a set", type = "ACCESSORY", game = null, extra = mapOf("accessoryType" to "SLEEVES"))

        val expected = listOf("$word Newest box", "$word Newest pack", "$word Middle box", "$word Older box", "$word Without a set")
        assertEquals(expected, names("sort=new"))
        // Without stock, the default order is the same.
        assertEquals(expected, names(""))
    }

    @Test
    fun `popular puts the popular sets first, in their order, then the newest sets`() {
        val unranked = set(LocalDate.of(2026, 9, 16))
        val second = set(LocalDate.of(2025, 1, 17), code = "popular-b")
        val first = set(LocalDate.of(2026, 1, 30), code = "popular-a")
        val older = set(LocalDate.of(2024, 5, 24))
        inSet(older, "$word Older")
        inSet(second, "$word Second")
        inSet(unranked, "$word Newest")
        inSet(first, "$word First")

        assertEquals(0, cardSetRepository.findByGameAndCode(Game.POKEMON, first)!!.popularRank)
        assertNull(cardSetRepository.findByGameAndCode(Game.POKEMON, unranked)!!.popularRank)
        assertEquals(listOf("$word First", "$word Second", "$word Newest", "$word Older"), names("sort=popular"))
    }

    @Test
    fun `the popular row on the home page shows one product per set`() {
        val first = set(LocalDate.of(2026, 1, 30), code = "popular-a")
        val second = set(LocalDate.of(2025, 1, 17), code = "popular-b")
        // In stock, so they come first within their set, whatever other tests put in these sets.
        val firstBox = inSet(first, "$word First box")
        adjustStock(firstBox["id"].asLong(), 2)
        inSet(first, "$word First pack", type = "BOOSTER_PACK").also { adjustStock(it["id"].asLong(), 2) }
        inSet(second, "$word Second box").also { adjustStock(it["id"].asLong(), 2) }

        val row = mockMvc.perform(get("/v1/products/bestsellers?limit=2")).andExpect(status().isOk).json()
        assertEquals(listOf("$word First box", "$word Second box"), row.map { it["name"].asText() })
    }
}
