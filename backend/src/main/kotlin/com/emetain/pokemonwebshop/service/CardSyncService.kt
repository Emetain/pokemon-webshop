package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.SyncRun
import com.emetain.pokemonwebshop.domain.SyncStatus
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.service.provider.CardDataProviders
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Runs the card sync in the background, at most one run per game at a time. */
@Service
class CardSyncService(
    private val providers: CardDataProviders,
    private val catalogImportService: CatalogImportService,
    private val syncRunService: SyncRunService,
    private val exchangeRateService: ExchangeRateService,
    private val pokedexImportService: PokedexImportService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // A private pool and not a bean: an Executor bean makes Spring Boot drop its applicationTaskExecutor,
    // and the @Async mails would then wait behind a sync that takes an hour.
    private val executor = Executors.newFixedThreadPool(
        Game.entries.size,
        Thread.ofPlatform().name("card-sync-", 1).daemon().factory(),
    )
    private val running = ConcurrentHashMap.newKeySet<Game>()

    /** Returns the new run right away. The work continues in the background. */
    fun start(game: Game): SyncRun {
        if (!running.add(game)) throw ApiException.syncRunning()
        try {
            val run = syncRunService.start(game)
            executor.execute { sync(game, run.id) }
            return run
        } catch (exception: RuntimeException) {
            running.remove(game)
            throw exception
        }
    }

    private fun sync(game: Game, runId: Long) {
        try {
            log.info("Card sync for {} started", game)
            refreshExchangeRates()
            if (game == Game.POKEMON) refreshPokedex()
            providers.forGame(game).fetchCatalog { set ->
                syncRunService.addProgress(runId, catalogImportService.importSet(game, set))
            }
            syncRunService.finish(runId, SyncStatus.COMPLETED)
            log.info("Card sync for {} finished", game)
        } catch (exception: Exception) {
            log.error("Card sync for {} failed", game, exception)
            syncRunService.finish(runId, SyncStatus.FAILED, exception.message ?: exception.toString())
        } finally {
            running.remove(game)
        }
    }

    /** Old rates are still fine for showing prices, so this never stops the sync. */
    private fun refreshExchangeRates() {
        try {
            exchangeRateService.refresh()
        } catch (exception: Exception) {
            log.warn("Keeping the current exchange rates: {}", exception.message)
        }
    }

    /** The Pokédex has nothing to do with the cards of tonight, so this never stops the sync either. */
    private fun refreshPokedex() {
        try {
            pokedexImportService.refresh()
        } catch (exception: Exception) {
            log.warn("Keeping the current Pokédex: {}", exception.message)
        }
    }

    @PreDestroy
    fun stop() {
        executor.shutdownNow()
    }
}
