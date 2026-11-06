package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.exception.ApiException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/** Starts the nightly sync of all games. */
@Service
class CatalogSyncJob(
    private val cardSyncService: CardSyncService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.catalog.sync-cron}", zone = "Europe/Amsterdam")
    fun run() {
        for (game in Game.entries) {
            try {
                cardSyncService.start(game)
            } catch (exception: ApiException) {
                log.warn("Skipping the nightly sync of {}: {}", game, exception.message)
            }
        }
    }
}
