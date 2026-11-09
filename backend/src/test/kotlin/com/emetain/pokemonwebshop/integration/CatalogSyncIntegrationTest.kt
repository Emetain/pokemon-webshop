package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.SyncRun
import com.emetain.pokemonwebshop.domain.SyncStatus
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.SyncRunRepository
import com.emetain.pokemonwebshop.service.CardSyncService
import com.emetain.pokemonwebshop.service.ExchangeRateService
import com.emetain.pokemonwebshop.service.PokedexImportService
import com.emetain.pokemonwebshop.service.SyncRunService
import com.emetain.pokemonwebshop.service.provider.CardDataProvider
import com.emetain.pokemonwebshop.service.provider.CardDataProviders
import com.emetain.pokemonwebshop.service.provider.ProviderSet
import org.junit.jupiter.api.BeforeEach
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogSyncIntegrationTest : IntegrationTest() {

    @MockBean
    private lateinit var providers: CardDataProviders

    @MockBean
    private lateinit var exchangeRateService: ExchangeRateService

    @MockBean
    private lateinit var pokedexImportService: PokedexImportService

    @Autowired
    private lateinit var cardSyncService: CardSyncService

    @Autowired
    private lateinit var syncRunService: SyncRunService

    @Autowired
    private lateinit var syncRunRepository: SyncRunRepository

    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var cardPriceRepository: CardPriceRepository

    private val game = Game.ONE_PIECE

    private var catalog: (onSet: (ProviderSet) -> Unit) -> Unit = {}

    @BeforeEach
    fun useFakeProvider() {
        val provider = object : CardDataProvider {
            override val game = this@CatalogSyncIntegrationTest.game
            override fun fetchCatalog(onSet: (ProviderSet) -> Unit) = catalog(onSet)
        }
        whenever(providers.forGame(game)) doReturn provider
    }

    private fun runToEnd(): SyncRun {
        val id = startWhenFree().id
        repeat(1000) {
            val run = syncRunRepository.findById(id).orElseThrow()
            if (run.status != SyncStatus.RUNNING) return run
            Thread.sleep(10)
        }
        error("The sync didn't finish")
    }

    // A finished run writes its status just before it lets go of the game.
    private fun startWhenFree(): SyncRun {
        repeat(1000) {
            try {
                return cardSyncService.start(game)
            } catch (exception: ApiException) {
                Thread.sleep(10)
            }
        }
        error("The game stayed busy")
    }

    @Test
    fun `syncing twice creates no duplicates and logs both runs`() {
        val set = providerSet(cards = listOf(providerCard(prices = listOf(providerPrice())), providerCard()))
        catalog = { it(set) }

        val first = runToEnd()
        val cards = cardRepository.countByGame(game)
        val prices = cardPriceRepository.countByGame(game)
        val second = runToEnd()

        assertEquals(SyncStatus.COMPLETED, first.status)
        assertEquals(1, first.setsProcessed)
        assertEquals(2, first.cardsCreated)
        assertEquals(1, first.pricesStored)
        assertNotNull(first.finishedAt)
        assertNull(first.error)

        assertEquals(SyncStatus.COMPLETED, second.status)
        assertEquals(0, second.cardsCreated)
        assertEquals(2, second.cardsUnchanged)

        assertEquals(cards, cardRepository.countByGame(game))
        assertEquals(prices, cardPriceRepository.countByGame(game))
    }

    @Test
    fun `a price removed at the source disappears on the next run`() {
        val id = uniqueCode()
        val code = uniqueCode()
        val prices = listOf(providerPrice(), providerPrice(CardVariants.REVERSE))
        catalog = { it(providerSet(code, listOf(providerCard(id, prices = prices)))) }
        runToEnd()

        catalog = { it(providerSet(code, listOf(providerCard(id, prices = prices.take(1))))) }
        val run = runToEnd()

        assertEquals(1, run.pricesStored)
        val card = cardRepository.findAllByGameAndExternalIdIn(game, listOf(id)).single()
        assertEquals(listOf(CardVariants.NORMAL), cardPriceRepository.findAllByCardIds(listOf(card.id)).map { it.variant })
    }

    @Test
    fun `a failing source is logged and keeps what was imported so far`() {
        val id = uniqueCode()
        catalog = { onSet ->
            onSet(providerSet(cards = listOf(providerCard(id))))
            error("The source fell over")
        }

        val run = runToEnd()

        assertEquals(SyncStatus.FAILED, run.status)
        assertEquals("The source fell over", run.error)
        assertEquals(1, run.setsProcessed)
        assertNotNull(run.finishedAt)
        assertEquals(1, cardRepository.findAllByGameAndExternalIdIn(game, listOf(id)).size)
    }

    @Test
    fun `a sync also refreshes the exchange rates`() {
        runToEnd()

        verify(exchangeRateService, atLeastOnce()).refresh()
    }

    @Test
    fun `when the exchange rates can't be fetched, the cards still sync`() {
        whenever(exchangeRateService.refresh()) doThrow IllegalStateException("ECB is down")
        catalog = { it(providerSet(cards = listOf(providerCard()))) }

        val run = runToEnd()

        assertEquals(SyncStatus.COMPLETED, run.status)
        assertEquals(1, run.cardsCreated)
    }

    @Test
    fun `only a Pokémon sync refreshes the Pokédex`() {
        runToEnd()

        verify(pokedexImportService, never()).refresh()
    }

    @Test
    fun `a Pokémon sync refreshes the Pokédex, and carries on when PokéAPI is down`() {
        val cards = mutableListOf<ProviderSet>()
        val provider = object : CardDataProvider {
            override val game = Game.POKEMON
            override fun fetchCatalog(onSet: (ProviderSet) -> Unit) {
                cards += providerSet()
            }
        }
        whenever(providers.forGame(Game.POKEMON)) doReturn provider
        whenever(pokedexImportService.refresh()) doThrow IllegalStateException("PokéAPI is down")

        val run = cardSyncService.start(Game.POKEMON)

        verify(pokedexImportService, timeout(5000)).refresh()
        // The run finishes in the background, which takes longer while the whole suite runs.
        await().atMost(Duration.ofSeconds(15))
            .until { syncRunRepository.findById(run.id).orElseThrow().status != SyncStatus.RUNNING }
        assertEquals(SyncStatus.COMPLETED, syncRunRepository.findById(run.id).orElseThrow().status)
        assertEquals(1, cards.size)
    }

    @Test
    fun `failed cards are counted without failing the run`() {
        catalog = { it(providerSet(failedCards = 2)) }

        val run = runToEnd()

        assertEquals(SyncStatus.COMPLETED, run.status)
        assertEquals(2, run.cardsFailed)
    }

    @Test
    fun `a very long error still fits in the log`() {
        catalog = { error("x".repeat(5000)) }

        assertEquals(2000, runToEnd().error?.length)
    }

    @Test
    fun `a second start for a busy game gives 409, and another game can still start`() {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        catalog = {
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
        }
        val otherGame = object : CardDataProvider {
            override val game = Game.LORCANA
            override fun fetchCatalog(onSet: (ProviderSet) -> Unit) = Unit
        }
        whenever(providers.forGame(Game.LORCANA)) doReturn otherGame
        val session = loggedInUser(Role.ADMIN)

        mockMvc.perform(post("/v1/admin/catalog/sync/one-piece").cookie(session))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.game").value("one-piece"))
        assertTrue(started.await(5, TimeUnit.SECONDS))

        mockMvc.perform(post("/v1/admin/catalog/sync/one-piece").cookie(session))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("sync_running"))
        mockMvc.perform(post("/v1/admin/catalog/sync/lorcana").cookie(session))
            .andExpect(status().isAccepted)

        release.countDown()
    }

    @Test
    fun `the admin overview shows the last run and the counts per game`() {
        catalog = { it(providerSet(cards = listOf(providerCard(prices = listOf(providerPrice()))))) }
        val run = runToEnd()
        val session = loggedInUser(Role.ADMIN)

        mockMvc.perform(get("/v1/admin/catalog/sync").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[?(@.game == 'one-piece')].lastRun.status").value("COMPLETED"))
            .andExpect(jsonPath("$[?(@.game == 'one-piece')].setCount").isNotEmpty)
        assertEquals(SyncStatus.COMPLETED, run.status)
    }

    @Test
    fun `an unknown game gives 404`() {
        mockMvc.perform(post("/v1/admin/catalog/sync/yugioh").cookie(loggedInUser(Role.ADMIN)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `the admin endpoints are closed to everyone else`() {
        val customer = loggedInUser(Role.CUSTOMER)

        mockMvc.perform(get("/v1/admin/catalog/sync")).andExpect(status().isUnauthorized)
        mockMvc.perform(post("/v1/admin/catalog/sync/pokemon")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/v1/admin/catalog/sync").cookie(customer)).andExpect(status().isForbidden)
        mockMvc.perform(post("/v1/admin/catalog/sync/pokemon").cookie(customer)).andExpect(status().isForbidden)
    }

    @Test
    fun `runs that were busy during a restart are marked as failed`() {
        val run = syncRunRepository.save(SyncRun(game = Game.MTG, startedAt = Instant.now()))

        syncRunService.failInterruptedRuns()

        val stored = syncRunRepository.findById(run.id).orElseThrow()
        assertEquals(SyncStatus.FAILED, stored.status)
        assertNotNull(stored.finishedAt)
        assertTrue(stored.error!!.contains("restart"))
    }
}
