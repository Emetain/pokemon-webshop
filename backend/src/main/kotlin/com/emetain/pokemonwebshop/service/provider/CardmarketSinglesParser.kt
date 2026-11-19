package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.CardmarketSingle
import com.emetain.pokemonwebshop.domain.Game
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class CardmarketSingleProduct(val id: Long, val name: String, val expansionId: Long, val metacardId: Long?)

data class CardmarketGuideEntry(
    val id: Long,
    val trend: Long?,
    val average: Long?,
    val foilTrend: Long?,
    val foilAverage: Long?,
)

data class CardmarketFile<T>(val createdAt: Instant?, val entries: List<T>)

/** Reads Cardmarket's singles files entry by entry, so a 15 MB file never sits in memory as one tree. */
object CardmarketSinglesParser {

    // "2026-11-19T02:48:38+0100": an offset without a colon, which ISO parsing refuses.
    private val CREATED_AT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

    fun products(parser: JsonParser): CardmarketFile<CardmarketSingleProduct> =
        read(parser, "products") { node ->
            val id = node.path("idProduct").asLong(0)
            val name = node.path("name").asString("").replace("\"\"", "\"").trim()
            val expansion = node.path("idExpansion").asLong(0)
            if (id == 0L || name.isEmpty() || expansion == 0L) {
                null
            } else {
                CardmarketSingleProduct(id, name, expansion, node.path("idMetacard").asLong(0).takeIf { it > 0 })
            }
        }

    fun priceGuide(parser: JsonParser): CardmarketFile<CardmarketGuideEntry> =
        read(parser, "priceGuides") { node ->
            val id = node.path("idProduct").asLong(0)
            if (id == 0L) {
                null
            } else {
                CardmarketGuideEntry(
                    id = id,
                    trend = PriceCents.parse(node.path("trend")),
                    average = PriceCents.parse(node.path("avg")),
                    // Pokémon calls its second column "holo", Magic "foil".
                    foilTrend = PriceCents.parse(node.path("trend-holo")) ?: PriceCents.parse(node.path("trend-foil")),
                    foilAverage = PriceCents.parse(node.path("avg-holo")) ?: PriceCents.parse(node.path("avg-foil")),
                )
            }
        }

    /** Products without a price guide entry are kept: an admin may still want to link a card to one. */
    fun combine(
        game: Game,
        products: CardmarketFile<CardmarketSingleProduct>,
        guide: CardmarketFile<CardmarketGuideEntry>,
        now: Instant,
    ): List<CardmarketSingle> {
        val prices = guide.entries.associateBy { it.id }
        val updatedAt = guide.createdAt ?: now
        return products.entries.distinctBy { it.id }.map { product ->
            val price = prices[product.id]
            CardmarketSingle(
                productId = product.id,
                game = game,
                name = product.name.take(NAME_LENGTH),
                expansionId = product.expansionId,
                metacardId = product.metacardId,
                trendCents = price?.trend,
                averageCents = price?.average,
                foilTrendCents = price?.foilTrend,
                foilAverageCents = price?.foilAverage,
                updatedAt = updatedAt,
            )
        }
    }

    private fun <T> read(parser: JsonParser, arrayField: String, map: (JsonNode) -> T?): CardmarketFile<T> =
        parser.use {
            var createdAt: Instant? = null
            val entries = mutableListOf<T>()
            if (parser.nextToken() != JsonToken.START_OBJECT) throw CatalogHttpException("Not a Cardmarket file")
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                val field = parser.currentName()
                val token = parser.nextToken()
                when {
                    field == "createdAt" && token == JsonToken.VALUE_STRING -> createdAt = instant(parser.getString())
                    field == arrayField && token == JsonToken.START_ARRAY -> {
                        while (parser.nextToken() == JsonToken.START_OBJECT) {
                            map(parser.readValueAsTree<JsonNode>())?.let(entries::add)
                        }
                    }
                    else -> parser.skipChildren()
                }
            }
            CardmarketFile(createdAt, entries)
        }

    private fun instant(text: String?): Instant? =
        text?.let {
            try {
                OffsetDateTime.parse(it, CREATED_AT).toInstant()
            } catch (exception: DateTimeParseException) {
                null
            }
        }

    private const val NAME_LENGTH = 255
}
