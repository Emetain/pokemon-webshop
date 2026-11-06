package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Game
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URLEncoder

/**
 * Pokémon from TCGdex. Rarity, Pokédex numbers and prices only exist on the card itself, so every card
 * needs its own request. That takes about an hour, which is why a single failing card doesn't stop a run.
 */
@Component
class TcgdexProvider(
    private val http: CatalogHttpClient,
    properties: AppProperties,
) : CardDataProvider {

    private val log = LoggerFactory.getLogger(javaClass)
    private val source = properties.catalog.tcgdex
    private val baseUrl = "${source.baseUrl}/en"

    override val game = Game.POKEMON

    override fun fetchCatalog(onSet: (ProviderSet) -> Unit) {
        val failures = FailureStreak()

        http.getJson("$baseUrl/sets", source.requestDelay).forEachIndexed { index, summary ->
            val set = http.getJson("$baseUrl/sets/${encode(summary.requireText("id"))}", source.requestDelay)
            if (set.path("serie").text("id") != TCG_POCKET_SERIES) {
                onSet(fetchSet(set, index, failures))
            }
        }
    }

    private fun fetchSet(set: JsonNode, sortOrder: Int, failures: FailureStreak): ProviderSet {
        val ids = set.path("cards").map { it.requireText("id") }
        val cards = ids.mapNotNull { fetchCard(it, failures) }
        return TcgdexMapper.set(set, sortOrder, cards, failedCards = ids.size - cards.size)
    }

    private fun fetchCard(id: String, failures: FailureStreak): ProviderCard? =
        try {
            TcgdexMapper.card(http.getJson("$baseUrl/cards/${encode(id)}", source.requestDelay))
                .also { failures.reset() }
        } catch (exception: CatalogHttpException) {
            log.warn("Skipping card {}: {}", id, exception.message)
            failures.add(exception)
            null
        } catch (exception: IllegalArgumentException) {
            log.warn("Skipping card {}: {}", id, exception.message)
            failures.add(exception)
            null
        }

    /** Some ids hold characters such as `%` and `!`, which have to survive the trip through the URL. */
    private fun encode(id: String): String = URLEncoder.encode(id, Charsets.UTF_8).replace("+", "%20")

    private class FailureStreak {
        private var count = 0

        fun reset() {
            count = 0
        }

        fun add(cause: Exception) {
            count++
            if (count >= MAX_FAILURES_IN_A_ROW) {
                throw CatalogHttpException("Stopped after $count failed cards in a row: ${cause.message}", cause)
            }
        }
    }

    private companion object {
        // Digital cards for the TCG Pocket app. The shop is about physical cards.
        const val TCG_POCKET_SERIES = "tcgp"

        // One bad card is the source's data. This many in a row means the API is down.
        const val MAX_FAILURES_IN_A_ROW = 25
    }
}
