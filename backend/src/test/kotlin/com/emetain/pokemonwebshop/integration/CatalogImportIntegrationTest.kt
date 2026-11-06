package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.ExchangeRate
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.PriceSource
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.ExchangeRateRepository
import com.emetain.pokemonwebshop.service.CatalogImportService
import com.emetain.pokemonwebshop.service.ImportResult
import com.emetain.pokemonwebshop.service.provider.ProviderPrice
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CatalogImportIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var importService: CatalogImportService

    @Autowired
    private lateinit var cardSetRepository: CardSetRepository

    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var cardPriceRepository: CardPriceRepository

    @Autowired
    private lateinit var exchangeRateRepository: ExchangeRateRepository

    @Autowired
    private lateinit var transaction: TransactionTemplate

    private val game = Game.POKEMON

    private fun card(externalId: String): Card =
        cardRepository.findAllByGameAndExternalIdIn(game, listOf(externalId)).single()

    private fun prices(externalId: String) = cardPriceRepository.findAllByCardIds(listOf(card(externalId).id))

    @Test
    fun `creates a set with its cards`() {
        val id = uniqueCode()
        val source = providerCard(id, number = "TG01", name = "Pokémon Center Lady", dexNumbers = setOf(3, 495))
        val set = providerSet(cards = listOf(source))

        val result = importService.importSet(game, set)

        assertEquals(ImportResult(created = 1), result)
        val stored = requireNotNull(cardSetRepository.findByGameAndCode(game, set.code))
        assertEquals(set.name, stored.name)
        assertEquals(1, stored.cardCount)
        transaction.executeWithoutResult {
            val card = card(id)
            assertEquals("pokemon center lady", card.nameSearch)
            assertEquals("TG000001", card.sortKey)
            assertEquals(stored.id, card.set.id)
            assertEquals(setOf(CardVariants.NORMAL), card.variants.toSet())
            assertEquals(setOf(3, 495), card.dexNumbers.toSet())
        }
    }

    @Test
    fun `importing the same set twice changes nothing`() {
        val id = uniqueCode()
        val set = providerSet(cards = listOf(providerCard(id, prices = listOf(providerPrice()))))

        val first = importService.importSet(game, set)
        val before = cardRepository.countByGame(game) to cardPriceRepository.countByGame(game)
        val second = importService.importSet(game, set)

        assertEquals(ImportResult(created = 1, pricesStored = 1), first)
        assertEquals(ImportResult(unchanged = 1, pricesStored = 1), second)
        assertEquals(before, cardRepository.countByGame(game) to cardPriceRepository.countByGame(game))
    }

    @Test
    fun `changes only count when a field really differs`() {
        val id = uniqueCode()
        val code = uniqueCode()
        importService.importSet(game, providerSet(code, listOf(providerCard(id, name = "Pikachu"))))

        val result = importService.importSet(game, providerSet(code, listOf(providerCard(id, name = "Raichu"))))

        assertEquals(ImportResult(updated = 1), result)
        assertEquals("Raichu", card(id).name)
        assertEquals("raichu", card(id).nameSearch)
    }

    @Test
    fun `updates variants and pokédex numbers`() {
        val id = uniqueCode()
        val code = uniqueCode()
        importService.importSet(game, providerSet(code, listOf(providerCard(id, dexNumbers = setOf(25)))))

        val changed = providerCard(id, variants = setOf(CardVariants.HOLO), dexNumbers = setOf(26, 27))
        val result = importService.importSet(game, providerSet(code, listOf(changed)))

        assertEquals(1, result.updated)
        transaction.executeWithoutResult {
            assertEquals(setOf(CardVariants.HOLO), card(id).variants.toSet())
            assertEquals(setOf(26, 27), card(id).dexNumbers.toSet())
        }
    }

    @Test
    fun `updates the set itself`() {
        val code = uniqueCode()
        importService.importSet(game, providerSet(code, name = "Old name"))

        importService.importSet(game, providerSet(code, name = "New name", releaseDate = LocalDate.of(2025, 2, 3)))

        val set = requireNotNull(cardSetRepository.findByGameAndCode(game, code))
        assertEquals("New name", set.name)
        assertEquals(LocalDate.of(2025, 2, 3), set.releaseDate)
    }

    @Test
    fun `a card that moved to another set follows`() {
        val id = uniqueCode()
        importService.importSet(game, providerSet(cards = listOf(providerCard(id))))
        val target = providerSet(cards = listOf(providerCard(id)))

        val result = importService.importSet(game, target)

        assertEquals(1, result.updated)
        transaction.executeWithoutResult { assertEquals(target.code, card(id).set.code) }
    }

    @Test
    fun `a new price is added and a changed one is updated in place`() {
        val id = uniqueCode()
        val code = uniqueCode()
        importService.importSet(game, providerSet(code, listOf(providerCard(id, prices = listOf(providerPrice(trend = 100))))))
        val priceId = prices(id).single().id

        val newPrices = listOf(providerPrice(trend = 150), providerPrice(CardVariants.REVERSE, trend = 300, average = null))
        val result = importService.importSet(game, providerSet(code, listOf(providerCard(id, prices = newPrices))))

        assertEquals(ImportResult(unchanged = 1, pricesStored = 2), result)
        val stored = prices(id).associateBy { it.variant }
        assertEquals(priceId, stored.getValue(CardVariants.NORMAL).id)
        assertEquals(150, stored.getValue(CardVariants.NORMAL).trendCents)
        assertEquals(300, stored.getValue(CardVariants.REVERSE).trendCents)
        assertNull(stored.getValue(CardVariants.REVERSE).averageCents)
    }

    @Test
    fun `stores the category and colours, and notices when they change`() {
        val id = uniqueCode()
        val code = uniqueCode()
        importService.importSet(game, providerSet(code, listOf(providerCard(id, category = "Trainer"))))

        val changed = providerCard(id, category = "Energy", colors = setOf("Fire"))
        val result = importService.importSet(game, providerSet(code, listOf(changed)))

        assertEquals(1, result.updated)
        transaction.executeWithoutResult {
            assertEquals("Energy", card(id).category)
            assertEquals(setOf("Fire"), card(id).colors.toSet())
        }
    }

    @Test
    fun `the sort price is the price lists show`() {
        val id = uniqueCode()
        val prices = listOf(providerPrice(CardVariants.REVERSE, trend = 900), providerPrice(CardVariants.NORMAL, trend = 300))

        importService.importSet(game, providerSet(cards = listOf(providerCard(id, prices = prices))))

        assertEquals(300, card(id).sortPriceCents)
    }

    @Test
    fun `a dollar price sorts by its value in euros`() {
        exchangeRateRepository.save(ExchangeRate(Currency.USD, BigDecimal("1.25"), LocalDate.of(2026, 11, 5)))
        val id = uniqueCode()
        val price = ProviderPrice(CardVariants.NORMAL, 1000, null, PRICE_DATE, PriceSource.TCGPLAYER, Currency.USD)

        importService.importSet(game, providerSet(cards = listOf(providerCard(id, prices = listOf(price)))))
        exchangeRateRepository.deleteAll()

        assertEquals(800, card(id).sortPriceCents)
    }

    @Test
    fun `a dollar price without a rate still sorts`() {
        exchangeRateRepository.deleteAll()
        val id = uniqueCode()
        val price = ProviderPrice(CardVariants.NORMAL, 1000, null, PRICE_DATE, PriceSource.TCGPLAYER, Currency.USD)

        importService.importSet(game, providerSet(cards = listOf(providerCard(id, prices = listOf(price)))))

        assertEquals(1000, card(id).sortPriceCents)
    }

    @Test
    fun `the sort price follows the price, without counting as a changed card`() {
        val id = uniqueCode()
        val code = uniqueCode()
        importService.importSet(game, providerSet(code, listOf(providerCard(id, prices = listOf(providerPrice(trend = 100))))))

        val result = importService.importSet(game, providerSet(code, listOf(providerCard(id, prices = listOf(providerPrice(trend = 250))))))

        assertEquals(1, result.unchanged)
        assertEquals(250, card(id).sortPriceCents)
    }

    @Test
    fun `a card that loses its price loses its sort price`() {
        val id = uniqueCode()
        val code = uniqueCode()
        importService.importSet(game, providerSet(code, listOf(providerCard(id, prices = listOf(providerPrice())))))

        importService.importSet(game, providerSet(code, listOf(providerCard(id))))

        assertNull(card(id).sortPriceCents)
    }

    @Test
    fun `a card with only an average has no sort price, like it has no price in lists`() {
        val id = uniqueCode()

        importService.importSet(game, providerSet(cards = listOf(providerCard(id, prices = listOf(providerPrice(trend = null))))))

        assertNull(card(id).sortPriceCents)
    }

    @Test
    fun `a price keeps its own source and currency`() {
        val id = uniqueCode()
        val price = ProviderPrice(CardVariants.NORMAL, 13, null, PRICE_DATE, PriceSource.TCGPLAYER, Currency.USD)

        importService.importSet(Game.LORCANA, providerSet(cards = listOf(providerCard(id, prices = listOf(price)))))

        val card = cardRepository.findAllByGameAndExternalIdIn(Game.LORCANA, listOf(id)).single()
        val stored = cardPriceRepository.findAllByCardIds(listOf(card.id)).single()
        assertEquals(PriceSource.TCGPLAYER, stored.source)
        assertEquals(Currency.USD, stored.currency)
    }

    @Test
    fun `two sources can each have a price for the same variant`() {
        val id = uniqueCode()
        val code = uniqueCode()
        val tcgplayer = ProviderPrice(CardVariants.NORMAL, 13, null, PRICE_DATE, PriceSource.TCGPLAYER, Currency.USD)
        val both = listOf(providerPrice(), tcgplayer)
        importService.importSet(game, providerSet(code, listOf(providerCard(id, prices = both))))

        importService.importSet(game, providerSet(code, listOf(providerCard(id, prices = listOf(tcgplayer)))))

        assertEquals(listOf(PriceSource.TCGPLAYER), prices(id).map { it.source })
    }

    @Test
    fun `a price the source no longer has disappears`() {
        val id = uniqueCode()
        val code = uniqueCode()
        val both = listOf(providerPrice(), providerPrice(CardVariants.REVERSE))
        importService.importSet(game, providerSet(code, listOf(providerCard(id, prices = both))))

        importService.importSet(game, providerSet(code, listOf(providerCard(id, prices = listOf(providerPrice())))))

        assertEquals(listOf(CardVariants.NORMAL), prices(id).map { it.variant })
    }

    @Test
    fun `a card the source no longer lists stays, with its price`() {
        val kept = uniqueCode()
        val code = uniqueCode()
        val cards = listOf(providerCard(kept, prices = listOf(providerPrice())), providerCard())
        importService.importSet(game, providerSet(code, cards))

        importService.importSet(game, providerSet(code, cards.drop(1)))

        assertNotNull(card(kept))
        assertEquals(1, prices(kept).size)
    }

    @Test
    fun `duplicate ids in the source become one card`() {
        val id = uniqueCode()

        val result = importService.importSet(game, providerSet(cards = listOf(providerCard(id), providerCard(id))))

        assertEquals(1, result.created)
    }

    @Test
    fun `duplicate variants in the source become one price`() {
        val id = uniqueCode()
        val prices = listOf(providerPrice(trend = 100), providerPrice(trend = 200))

        importService.importSet(game, providerSet(cards = listOf(providerCard(id, prices = prices))))

        assertEquals(100, prices(id).single().trendCents)
    }

    @Test
    fun `text longer than the column is cut`() {
        val id = uniqueCode()
        val source = providerCard(id, number = "9".repeat(80), name = "x".repeat(400), rarity = "r".repeat(80))

        importService.importSet(game, providerSet(cards = listOf(source), name = "n".repeat(400), series = "s".repeat(200)))

        val card = card(id)
        assertEquals(50, card.number.length)
        assertEquals(255, card.name.length)
        assertEquals(50, card.rarity?.length)
    }

    @Test
    fun `importing again doesn't count a long text as changed`() {
        val id = uniqueCode()
        val code = uniqueCode()
        val set = providerSet(code, listOf(providerCard(id, name = "x".repeat(400))))
        importService.importSet(game, set)

        assertEquals(ImportResult(unchanged = 1), importService.importSet(game, set))
    }

    @Test
    fun `reports the cards the provider failed to deliver`() {
        assertEquals(3, importService.importSet(game, providerSet(failedCards = 3)).failed)
    }

    @Test
    fun `the same id in another game is another card`() {
        val id = uniqueCode()
        importService.importSet(Game.POKEMON, providerSet(cards = listOf(providerCard(id))))

        val result = importService.importSet(Game.LORCANA, providerSet(cards = listOf(providerCard(id))))

        assertEquals(1, result.created)
    }
}
