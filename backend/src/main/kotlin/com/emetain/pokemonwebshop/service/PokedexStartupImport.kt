package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * Fills the Pokédex right after startup, so it doesn't have to wait for the nightly sync. Once it's complete this
 * costs a single request. It runs in the background, because the first import takes about a quarter of an hour.
 */
@Component
class PokedexStartupImport(
    private val pokedexImportService: PokedexImportService,
    private val properties: AppProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener(ApplicationReadyEvent::class)
    fun run() {
        if (!properties.catalog.importPokedexOnStart) return
        try {
            pokedexImportService.refresh()
        } catch (exception: Exception) {
            log.warn("The Pokédex will be completed by the next Pokémon sync: {}", exception.message)
        }
    }
}
