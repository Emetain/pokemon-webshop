package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.service.CatalogImportService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

class CatalogApiIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var importService: CatalogImportService

    @Autowired
    private lateinit var cardRepository: CardRepository

    private val code = "${uniqueCode()}.5"
    private val word = uniqueWord()

    private lateinit var charizardId: String

    @BeforeEach
    fun importSet() {
        charizardId = uniqueCode()
        val cards = listOf(
            providerCard(number = "10", name = "Ten $word"),
            providerCard(number = "2", name = "$word Two", rarity = "Common"),
            providerCard(
                charizardId,
                number = "TG01",
                name = "Charizard $word",
                variants = setOf(CardVariants.REVERSE, CardVariants.NORMAL, CardVariants.FIRST_EDITION),
                dexNumbers = setOf(6),
                prices = listOf(
                    providerPrice(CardVariants.REVERSE, trend = 500, average = 550),
                    providerPrice(CardVariants.NORMAL, trend = 12363, average = 13000),
                ),
            ),
            providerCard(number = "3", name = "No Image", rarity = null, variants = setOf(CardVariants.HOLO)),
        )
        importService.importSet(Game.POKEMON, providerSet(code, cards, name = "Test Set", series = "Test Series"))
    }

    private fun fetch(path: String): ResultActions = mockMvc.perform(get("/v1/catalog$path"))

    private fun charizard() = cardRepository.findAllByGameAndExternalIdIn(Game.POKEMON, listOf(charizardId)).single()

    @Test
    fun `lists sets of a game, newest first`() {
        val older = providerSet(releaseDate = LocalDate.of(1999, 1, 9))
        val newer = providerSet(releaseDate = LocalDate.of(2030, 1, 1))
        val undated = providerSet(releaseDate = null)
        listOf(older, undated, newer).forEach { importService.importSet(Game.MTG, it) }

        fetch("/magic/sets")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].code").value(newer.code))
            .andExpect(jsonPath("$[0].game").value("magic"))
            .andExpect(jsonPath("$[-1:].code").value(undated.code))
    }

    @Test
    fun `an unknown game gives 404`() {
        fetch("/yugioh/sets").andExpect(status().isNotFound).andExpect(jsonPath("$.error").value("not_found"))
        fetch("/yugioh/sets/base1").andExpect(status().isNotFound)
        fetch("/yugioh/sets/base1/cards").andExpect(status().isNotFound)
        fetch("/search?q=pikachu&game=yugioh").andExpect(status().isNotFound)
    }

    @Test
    fun `a set shows the rarities and variants that exist in it`() {
        fetch("/pokemon/sets/$code")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.set.name").value("Test Set"))
            .andExpect(jsonPath("$.set.series").value("Test Series"))
            .andExpect(jsonPath("$.set.cardCount").value(4))
            .andExpect(jsonPath("$.rarities.length()").value(2))
            .andExpect(jsonPath("$.rarities[0]").value("Common"))
            .andExpect(jsonPath("$.rarities[1]").value("Rare"))
            .andExpect(jsonPath("$.variants[0]").value("normal"))
            .andExpect(jsonPath("$.variants[1]").value("holo"))
            .andExpect(jsonPath("$.variants[2]").value("reverse"))
            .andExpect(jsonPath("$.variants[3]").value("firstEdition"))
    }

    @Test
    fun `an unknown set gives 404`() {
        fetch("/pokemon/sets/nope").andExpect(status().isNotFound)
        fetch("/pokemon/sets/nope/cards").andExpect(status().isNotFound)
    }

    @Test
    fun `the same code in another game is another set`() {
        fetch("/lorcana/sets/$code").andExpect(status().isNotFound)
    }

    @Test
    fun `cards are sorted by number, with digits by value`() {
        fetch("/pokemon/sets/$code/cards")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(4))
            .andExpect(jsonPath("$.items[0].number").value("2"))
            .andExpect(jsonPath("$.items[1].number").value("3"))
            .andExpect(jsonPath("$.items[2].number").value("10"))
            .andExpect(jsonPath("$.items[3].number").value("TG01"))
    }

    @Test
    fun `a card in a list shows the trend price of its most common finish`() {
        fetch("/pokemon/sets/$code/cards")
            .andExpect(jsonPath("$.items[3].trendCents").value(12363))
            .andExpect(jsonPath("$.items[3].currency").value("EUR"))
            .andExpect(jsonPath("$.items[3].setName").value("Test Set"))
            .andExpect(jsonPath("$.items[3].imageUrl").isNotEmpty)
            .andExpect(jsonPath("$.items[0].trendCents").isEmpty)
            .andExpect(jsonPath("$.items[0].currency").isEmpty)
    }

    @Test
    fun `filters by rarity`() {
        fetch("/pokemon/sets/$code/cards?rarity=Common")
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].number").value("2"))
    }

    @Test
    fun `filters by variant`() {
        fetch("/pokemon/sets/$code/cards?variant=holo")
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].number").value("3"))
    }

    @Test
    fun `combines filters`() {
        fetch("/pokemon/sets/$code/cards?rarity=Rare&variant=reverse")
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].number").value("TG01"))
    }

    @Test
    fun `an empty filter means no filter`() {
        fetch("/pokemon/sets/$code/cards?rarity=&variant=").andExpect(jsonPath("$.totalItems").value(4))
        fetch("/search?q=$word&game=&set=&rarity=")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.results.totalItems").value(3))
    }

    private fun sortableSet(): String {
        val sortCode = uniqueCode()
        val cards = listOf(
            providerCard(number = "1", name = "Zapdos", category = "Pokemon", colors = setOf("Lightning"), prices = listOf(providerPrice(trend = 500))),
            providerCard(number = "2", name = "Abra", category = "Pokemon", colors = setOf("Psychic"), prices = listOf(providerPrice(trend = 9000))),
            providerCard(number = "3", name = "Mew", category = "Pokemon", colors = setOf("Psychic", "Fire")),
            providerCard(number = "4", name = "Potion", category = "Trainer", prices = listOf(providerPrice(trend = 25))),
            providerCard(number = "5", name = "Fire Energy", category = "Energy", colors = setOf("Fire")),
        )
        importService.importSet(Game.POKEMON, providerSet(sortCode, cards))
        return sortCode
    }

    private fun names(path: String): List<String> {
        val body = fetch(path).andExpect(status().isOk).andReturn().response.contentAsString
        val root = objectMapper.readTree(body)
        return (root.path("results").takeIf { it.isObject } ?: root).path("items").map { it.path("name").asText() }
    }

    @Test
    fun `sorts by number unless asked otherwise`() {
        val set = sortableSet()

        val expected = listOf("Zapdos", "Abra", "Mew", "Potion", "Fire Energy")
        assert(names("/pokemon/sets/$set/cards") == expected)
        assert(names("/pokemon/sets/$set/cards?sort=number") == expected)
        assert(names("/pokemon/sets/$set/cards?sort=nonsense") == expected)
    }

    @Test
    fun `sorts by name in both directions`() {
        val set = sortableSet()

        assert(names("/pokemon/sets/$set/cards?sort=name") == listOf("Abra", "Fire Energy", "Mew", "Potion", "Zapdos"))
        assert(names("/pokemon/sets/$set/cards?sort=name-desc") == listOf("Zapdos", "Potion", "Mew", "Fire Energy", "Abra"))
    }

    @Test
    fun `sorts by price with cards without a price last, in both directions`() {
        val set = sortableSet()

        assert(names("/pokemon/sets/$set/cards?sort=price-high") == listOf("Abra", "Zapdos", "Potion", "Mew", "Fire Energy"))
        assert(names("/pokemon/sets/$set/cards?sort=price-low") == listOf("Potion", "Zapdos", "Abra", "Mew", "Fire Energy"))
    }

    @Test
    fun `price sorting holds across pages`() {
        val set = sortableSet()

        val pages = (0..4).flatMap { names("/pokemon/sets/$set/cards?sort=price-high&size=1&page=$it") }

        assert(pages == listOf("Abra", "Zapdos", "Potion", "Mew", "Fire Energy")) { "Got $pages" }
    }

    @Test
    fun `filters by category, such as trainer cards`() {
        val set = sortableSet()

        assert(names("/pokemon/sets/$set/cards?category=Trainer") == listOf("Potion"))
        assert(names("/pokemon/sets/$set/cards?category=Energy") == listOf("Fire Energy"))
    }

    @Test
    fun `filters by energy type, including cards with two`() {
        val set = sortableSet()

        assert(names("/pokemon/sets/$set/cards?color=Psychic") == listOf("Abra", "Mew"))
        assert(names("/pokemon/sets/$set/cards?color=Fire") == listOf("Mew", "Fire Energy"))
    }

    @Test
    fun `can leave out cards without a price`() {
        val set = sortableSet()

        assert(names("/pokemon/sets/$set/cards?priced=true") == listOf("Zapdos", "Abra", "Potion"))
        assert(names("/pokemon/sets/$set/cards?priced=false").size == 5)
    }

    @Test
    fun `filters and sorting combine, and the total matches`() {
        val set = sortableSet()

        fetch("/pokemon/sets/$set/cards?category=Pokemon&color=Psychic&priced=true&sort=name")
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].name").value("Abra"))
    }

    @Test
    fun `a set offers the categories and colours that exist in it`() {
        val set = sortableSet()

        fetch("/pokemon/sets/$set")
            .andExpect(jsonPath("$.categories.length()").value(3))
            .andExpect(jsonPath("$.categories[0]").value("Energy"))
            .andExpect(jsonPath("$.colors.length()").value(3))
            .andExpect(jsonPath("$.colors[0]").value("Fire"))
    }

    @Test
    fun `a card shows its colours`() {
        val id = uniqueCode()
        importService.importSet(Game.LORCANA, providerSet(cards = listOf(providerCard(id, colors = setOf("Ruby", "Amber")))))
        val card = cardRepository.findAllByGameAndExternalIdIn(Game.LORCANA, listOf(id)).single()

        fetch("/cards/${card.id}").andExpect(jsonPath("$.colors[0]").value("Amber")).andExpect(jsonPath("$.colors[1]").value("Ruby"))
    }

    @Test
    fun `search can sort and filter too, and keeps its best match first by default`() {
        val term = uniqueWord()
        val cards = listOf(
            providerCard(number = "1", name = "Big $term", category = "Pokemon", colors = setOf("Fire"), prices = listOf(providerPrice(trend = 900))),
            providerCard(number = "2", name = "$term Small", category = "Trainer", prices = listOf(providerPrice(trend = 100))),
        )
        importService.importSet(Game.POKEMON, providerSet(cards = cards))

        assert(names("/search?q=$term") == listOf("$term Small", "Big $term"))
        assert(names("/search?q=$term&sort=price-high") == listOf("Big $term", "$term Small"))
        assert(names("/search?q=$term&category=Trainer") == listOf("$term Small"))
        assert(names("/search?q=$term&color=Fire&priced=true") == listOf("Big $term"))
        fetch("/search?q=$term&game=pokemon")
            .andExpect(jsonPath("$.categories[?(@ == 'Trainer')]").isNotEmpty)
            .andExpect(jsonPath("$.colors[?(@ == 'Fire')]").isNotEmpty)
        fetch("/search?q=$term").andExpect(jsonPath("$.categories.length()").value(0))
    }

    @Test
    fun `pages through a set without skipping or repeating`() {
        fetch("/pokemon/sets/$code/cards?size=3&page=0")
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
        fetch("/pokemon/sets/$code/cards?size=3&page=1")
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].number").value("TG01"))
    }

    @Test
    fun `cards with the same number keep a stable order between pages`() {
        val sameNumber = uniqueCode()
        val cards = (1..5).map { providerCard(number = "OP01-001", name = "Alternate $it") }
        importService.importSet(Game.ONE_PIECE, providerSet(sameNumber, cards))

        val seen = (0..4).map { page ->
            val body = fetch("/one-piece/sets/$sameNumber/cards?size=1&page=$page").andReturn().response.contentAsString
            objectMapper.readTree(body).path("items").path(0).path("name").asText()
        }

        assert(seen.toSet().size == 5) { "Got $seen" }
    }

    @Test
    fun `the page size has a default and a maximum`() {
        fetch("/pokemon/sets/$code/cards").andExpect(jsonPath("$.size").value(60))
        fetch("/pokemon/sets/$code/cards?size=1000").andExpect(jsonPath("$.size").value(100))
        fetch("/pokemon/sets/$code/cards?size=0").andExpect(jsonPath("$.size").value(1))
        fetch("/pokemon/sets/$code/cards?page=-1").andExpect(status().isOk).andExpect(jsonPath("$.page").value(0))
    }

    @Test
    fun `a card shows all its details and prices`() {
        fetch("/cards/${charizard().id}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Charizard $word"))
            .andExpect(jsonPath("$.game").value("pokemon"))
            .andExpect(jsonPath("$.set.code").value(code))
            .andExpect(jsonPath("$.number").value("TG01"))
            .andExpect(jsonPath("$.rarity").value("Rare"))
            .andExpect(jsonPath("$.cardType").value("Pokemon"))
            .andExpect(jsonPath("$.imageLargeUrl").isNotEmpty)
            .andExpect(jsonPath("$.variants[0]").value("normal"))
            .andExpect(jsonPath("$.variants[1]").value("reverse"))
            .andExpect(jsonPath("$.variants[2]").value("firstEdition"))
            .andExpect(jsonPath("$.dexNumbers[0]").value(6))
            .andExpect(jsonPath("$.prices.length()").value(2))
            .andExpect(jsonPath("$.prices[0].variant").value("normal"))
            .andExpect(jsonPath("$.prices[0].source").value("CARDMARKET"))
            .andExpect(jsonPath("$.prices[0].currency").value("EUR"))
            .andExpect(jsonPath("$.prices[0].trendCents").value(12363))
            .andExpect(jsonPath("$.prices[0].averageCents").value(13000))
            .andExpect(jsonPath("$.prices[0].updatedAt").value("2026-11-05T10:00:00Z"))
            .andExpect(jsonPath("$.prices[1].variant").value("reverse"))
    }

    @Test
    fun `an unknown card gives 404`() {
        fetch("/cards/999999999").andExpect(status().isNotFound)
    }

    @Test
    fun `searching needs at least 2 characters`() {
        fetch("/search?q=a").andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("validation_failed"))
        fetch("/search?q=--").andExpect(status().isBadRequest)
        fetch("/search").andExpect(status().isBadRequest)
    }

    @Test
    fun `search finds names anywhere and puts names that start with the term first`() {
        fetch("/search?q=$word&game=pokemon")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.results.totalItems").value(3))
            .andExpect(jsonPath("$.results.items[0].name").value("$word Two"))
            .andExpect(jsonPath("$.results.items[1].name").value("Charizard $word"))
            .andExpect(jsonPath("$.results.items[2].name").value("Ten $word"))
    }

    @Test
    fun `search ignores case, accents and punctuation`() {
        val special = uniqueWord()
        importService.importSet(Game.POKEMON, providerSet(cards = listOf(providerCard(name = "Pokémon-$special Ho"))))

        fetch("/search?q=POKEMON $special").andExpect(jsonPath("$.results.totalItems").value(1))
        fetch("/search?q=pokémon-$special ho").andExpect(jsonPath("$.results.totalItems").value(1))
    }

    @Test
    fun `search filters by game, set and rarity`() {
        importService.importSet(Game.LORCANA, providerSet(cards = listOf(providerCard(name = "Lorcana $word"))))

        fetch("/search?q=$word").andExpect(jsonPath("$.results.totalItems").value(4))
        fetch("/search?q=$word&game=lorcana").andExpect(jsonPath("$.results.totalItems").value(1))
        fetch("/search?q=$word&set=$code").andExpect(jsonPath("$.results.totalItems").value(3))
        fetch("/search?q=$word&game=pokemon&rarity=Common").andExpect(jsonPath("$.results.totalItems").value(1))
    }

    @Test
    fun `search pages and shows prices`() {
        fetch("/search?q=$word&game=pokemon&size=2&page=1")
            .andExpect(jsonPath("$.results.items.length()").value(1))
            .andExpect(jsonPath("$.results.totalPages").value(2))
        fetch("/search?q=charizard $word").andExpect(jsonPath("$.results.items[0].trendCents").value(12363))
    }

    @Test
    fun `search gives the rarities of the chosen game as filter options`() {
        fetch("/search?q=$word&game=pokemon").andExpect(jsonPath("$.rarities[?(@ == 'Common')]").isNotEmpty)
        fetch("/search?q=$word").andExpect(jsonPath("$.rarities.length()").value(0))
    }

    @Test
    fun `the catalog needs no login`() {
        fetch("/pokemon/sets/$code/cards").andExpect(status().isOk)
    }
}
