package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.ReleaseSource
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.service.pictures.PictureSearchService
import com.emetain.pokemonwebshop.service.pictures.TcgGroup
import com.emetain.pokemonwebshop.service.pictures.TcgProduct
import com.emetain.pokemonwebshop.service.pictures.TcgplayerImages
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pictures from TCGplayer for cards and products without one, and the release calendar suggestions. */
class PictureSearchIntegrationTest : ShopTestSupport() {

    @Autowired
    private lateinit var catalog: FakeTcgplayerCatalog

    @Autowired
    private lateinit var searchService: PictureSearchService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var transactions: TransactionTemplate

    private fun id() = Random.nextLong(1_000_000, 9_000_000)

    private fun cardImage(cardId: Long) = transactions.execute { cardRepository.findById(cardId).orElseThrow().shownImageLargeUrl }

    private fun productImages(productId: Long) = transactions.execute { productRepository.findById(productId).orElseThrow().images.toList() }!!

    /** A set of the shop with two cards the source has no picture for, and its counterpart at TCGplayer. */
    private fun setWithoutPictures(): Triple<String, List<Long>, Map<String, Long>> {
        val code = uniqueCode()
        val setName = "$word Sparks"
        val noPicture = { number: String, name: String -> providerCard(number = number, name = name).copy(imageSmallUrl = null, imageLargeUrl = null) }
        importService.importSet(
            Game.POKEMON,
            providerSet(code, listOf(noPicture("003", "Masquerain"), noPicture("132", "Bubbly Water Energy")), name = setName, releaseDate = LocalDate.of(2024, 11, 8)),
        )
        val cards = transactions.execute {
            val set = cardSetRepository.findByGameAndCode(Game.POKEMON, code)!!
            cardRepository.findWithoutPicture(Game.POKEMON).filter { it.set.id == set.id }.sortedBy { it.number }.map { it.id }
        }!!
        val tcg = mapOf(
            "masquerain" to id(), "energy" to id(), "box" to id(), "lgs" to id(), "retail" to id(), "pack" to id(),
        )
        catalog.add(
            Game.POKEMON,
            TcgGroup(id(), "SV08: $setName", "SSP", LocalDate.of(2024, 11, 8)),
            listOf(
                TcgProduct(tcg.getValue("masquerain"), "Masquerain - 003/191", "003/191"),
                TcgProduct(tcg.getValue("energy"), "Bubbly W Energy", "132/191"),
                TcgProduct(tcg.getValue("box"), "$setName Booster Box", null),
                TcgProduct(id(), "$setName Booster Box Case", null),
                TcgProduct(tcg.getValue("lgs"), "$setName Booster Bundle (LGS)", null),
                TcgProduct(tcg.getValue("retail"), "$setName Booster Bundle (Retail)", null),
                TcgProduct(tcg.getValue("pack"), "$setName Booster Pack", null),
            ),
        )
        return Triple(code, cards, tcg)
    }

    private fun suggestions(): JsonNode = mockMvc.perform(get("/v1/admin/pictures/suggestions?game=pokemon").cookie(admin)).andExpect(status().isOk).json()

    @Test
    fun `sure matches get their picture, doubtful ones wait for the admin, and a rejected one doesn't come back`() {
        val (code, cards, tcg) = setWithoutPictures()
        val box = createProduct(name = "$word Sparks Booster Box", extra = mapOf("setCode" to code))["id"].asLong()
        val bundle = createProduct(name = "$word Sparks Booster Bundle", type = "BOOSTER_BUNDLE", extra = mapOf("setCode" to code))["id"].asLong()
        val japanese = createProduct(name = "$word Sparks Booster (Japanese)", type = "BOOSTER_PACK", extra = mapOf("setCode" to code))["id"].asLong()

        val status = searchService.run(Game.POKEMON)
        assertFalse(status.running)
        assertNull(status.error)
        assertTrue(status.cardsFilled >= 1 && status.productsFilled >= 1 && status.suggested >= 2 && status.skipped >= 1, "$status")

        assertEquals(TcgplayerImages.large(tcg.getValue("masquerain")), cardImage(cards[0]), "Masquerain, number 003: sure")
        assertNull(cardImage(cards[1]), "Bubbly W Energy is another name: the admin decides")
        assertEquals(listOf(TcgplayerImages.large(tcg.getValue("box"))), productImages(box), "the box, not the case")
        assertEquals(emptyList(), productImages(bundle), "LGS or Retail: the admin decides")
        assertEquals(emptyList(), productImages(japanese), "no English picture for a Japanese product")

        val open = suggestions()["suggestions"].filter { it["setName"].asText() == "$word Sparks" }
        val energy = open.single { it["kind"].asText() == "CARD" }
        val bundleSuggestion = open.single { it["kind"].asText() == "PRODUCT" }
        assertEquals("132", energy["detail"].asText())
        assertEquals(setOf(tcg.getValue("lgs"), tcg.getValue("retail")), bundleSuggestion["candidates"].take(2).map { it["tcgplayerId"].asLong() }.toSet())

        mockMvc.perform(
            post("/v1/admin/pictures/suggestions/${energy["id"].asLong()}/accept").cookie(admin).json(mapOf("tcgplayerId" to tcg.getValue("energy"))),
        ).andExpect(status().isOk)
        assertEquals(TcgplayerImages.large(tcg.getValue("energy")), cardImage(cards[1]))
        mockMvc.perform(post("/v1/admin/pictures/suggestions/${energy["id"].asLong()}/reject").cookie(admin)).andExpect(status().isConflict)

        mockMvc.perform(post("/v1/admin/pictures/suggestions/${bundleSuggestion["id"].asLong()}/accept").cookie(admin).json(mapOf("tcgplayerId" to 1)))
            .andExpect(status().isBadRequest)
        mockMvc.perform(post("/v1/admin/pictures/suggestions/${bundleSuggestion["id"].asLong()}/reject").cookie(admin)).andExpect(status().isOk)

        val again = searchService.run(Game.POKEMON)
        assertNull(again.error)
        assertTrue(suggestions()["suggestions"].none { it["setName"].asText() == "$word Sparks" }, "nothing new: the rest has a picture or was rejected")
        assertEquals(emptyList(), productImages(bundle))

        // The catalog shows the found picture, and an upload still goes first.
        val card = mockMvc.perform(get("/v1/catalog/cards/${cards[0]}")).andExpect(status().isOk).json()
        assertEquals(TcgplayerImages.large(tcg.getValue("masquerain")), card["imageLargeUrl"].asText())
    }

    @Test
    fun `the overview counts what has no picture, and only admins start a search`() {
        val overview = mockMvc.perform(get("/v1/admin/pictures/search").cookie(admin)).andExpect(status().isOk).json()
        assertEquals(Game.entries.map { it.slug }.toSet(), overview.map { it["game"].asText() }.toSet())
        mockMvc.perform(post("/v1/admin/pictures/search/pokemon").cookie(loggedInUser())).andExpect(status().isForbidden)
        mockMvc.perform(post("/v1/admin/pictures/search/fortnite").cookie(admin)).andExpect(status().isBadRequest)
    }

    @Test
    fun `release suggestions come from the catalog and TCGplayer, once per set, and leave when dismissed or added`() {
        val soon = LocalDate.now().plusDays(40)
        val code = uniqueCode()
        importService.importSet(Game.MTG, providerSet(code, emptyList(), name = "$word Realm", releaseDate = soon))
        importService.importSet(Game.MTG, providerSet(uniqueCode(), emptyList(), name = "$word Realm Tokens", releaseDate = soon))
        catalog.add(Game.MTG, TcgGroup(id(), "$word Realm", "XYZ", soon.plusDays(1)))
        catalog.add(Game.LORCANA, TcgGroup(id(), "$word Inkdark", null, soon.plusDays(10)))
        catalog.add(Game.LORCANA, TcgGroup(id(), "$word Old Chapter", null, LocalDate.now().minusDays(10)))

        val list = mockMvc.perform(post("/v1/admin/release-suggestions/refresh").cookie(admin)).andExpect(status().isOk).json()
            .filter { it["name"].asText().startsWith(word) }
        assertEquals(listOf("$word Realm", "$word Inkdark"), list.map { it["name"].asText() })
        val realm = list.first()
        assertEquals(ReleaseSource.CATALOG.name, realm["source"].asText())
        assertEquals(code, realm["setCode"].asText())
        assertEquals(soon.toString(), realm["releaseDate"].asText(), "the catalog's date wins")

        mockMvc.perform(post("/v1/admin/release-suggestions/${list[1]["id"].asLong()}/dismiss").cookie(admin)).andExpect(status().isNoContent)
        mockMvc.perform(
            post("/v1/admin/releases").cookie(admin).json(mapOf("game" to Game.MTG.slug, "name" to "$word Realm", "releaseDate" to soon.toString())),
        ).andExpect(status().isCreated)
        mockMvc.perform(post("/v1/admin/release-suggestions/refresh").cookie(admin)).andExpect(status().isOk)
        val left = mockMvc.perform(get("/v1/admin/release-suggestions").cookie(admin)).json().filter { it["name"].asText().startsWith(word) }
        assertEquals(emptyList(), left.map { it["name"].asText() }, "one dismissed, one on the calendar")
    }
}
