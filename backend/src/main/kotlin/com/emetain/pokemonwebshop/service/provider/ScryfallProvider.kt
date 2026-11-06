package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Game
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.util.zip.GZIPInputStream

/**
 * Magic from Scryfall's daily bulk file, which replaces thousands of API calls. The file has one card per
 * line and isn't ordered by set, so the cards are grouped in memory first (about 100 MB of heap).
 */
@Component
class ScryfallProvider(
    private val http: CatalogHttpClient,
    private val objectMapper: ObjectMapper,
    properties: AppProperties,
) : CardDataProvider {

    private val source = properties.catalog.scryfall

    override val game = Game.MTG

    override fun fetchCatalog(onSet: (ProviderSet) -> Unit) {
        val bulkFile = http.getJson("${source.baseUrl}/bulk-data", source.requestDelay)
            .path("data")
            .first { it.text("type") == BULK_TYPE }
        val updatedAt = OffsetDateTime.parse(bulkFile.requireText("updated_at")).toInstant()

        val cardsBySet = http.get(bulkFile.requireText("jsonl_download_uri"), source.requestDelay) { body ->
            readCards(body, updatedAt)
        }

        // Scryfall lists the newest set first, and a higher sort order means newer.
        val sets = http.getJson("${source.baseUrl}/sets", source.requestDelay).path("data")
            .filterNot(ScryfallMapper::isDigital)
        sets.forEachIndexed { index, set ->
            val cards = cardsBySet.remove(set.requireText("code")).orEmpty()
            onSet(ScryfallMapper.set(set, sortOrder = sets.size - index, cards))
        }
    }

    private fun readCards(body: InputStream, updatedAt: Instant): MutableMap<String, MutableList<ProviderCard>> {
        val cardsBySet = mutableMapOf<String, MutableList<ProviderCard>>()
        GZIPInputStream(body).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }
                .map(objectMapper::readTree)
                .filterNot(ScryfallMapper::isDigital)
                .forEach { card ->
                    cardsBySet.getOrPut(card.requireText("set")) { mutableListOf() }
                        .add(ScryfallMapper.card(card, updatedAt))
                }
        }
        return cardsBySet
    }

    private companion object {
        const val BULK_TYPE = "default_cards"
    }
}
