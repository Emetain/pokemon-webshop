package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceSource
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

/**
 * Maps Lorcast JSON to our model. Lorcast doesn't say where its prices come from, but they match TCGplayer's
 * market price (checked on 204 cards through `tcgplayer_id`). They have no date, and change once a day.
 */
object LorcastMapper {

    const val MAIN_SERIES = "main"
    const val SPECIAL_SERIES = "special"

    fun set(json: JsonNode, sortOrder: Int, cards: List<ProviderCard>): ProviderSet {
        val code = json.requireText("code")
        return ProviderSet(
            externalId = json.requireText("id"),
            code = code,
            name = json.requireText("name"),
            series = if (code.all(Char::isDigit)) MAIN_SERIES else SPECIAL_SERIES,
            releaseDate = json.date("released_at"),
            cardCount = cards.size,
            logoUrl = null,
            symbolUrl = null,
            sortOrder = sortOrder,
            cards = cards,
        )
    }

    fun card(json: JsonNode, fetchedAt: Instant): ProviderCard {
        val images = json.path("image_uris").path("digital")
        val prices = json.path("prices")
        return ProviderCard(
            externalId = json.requireText("id"),
            number = json.requireText("collector_number"),
            name = listOfNotNull(json.requireText("name"), json.text("version")).joinToString(" - "),
            rarity = json.text("rarity")?.replace('_', ' '),
            cardType = json.path("type").joinToString(" - ") { it.asText() }.ifBlank { null },
            category = json.path("type").firstOrNull()?.asText(),
            imageSmallUrl = images.text("normal"),
            imageLargeUrl = images.text("large"),
            variants = variants(prices),
            colors = json.path("inks").map { it.asText() }.ifEmpty { listOfNotNull(json.text("ink")) }.toSet(),
            prices = prices(prices, fetchedAt),
        )
    }

    private fun prices(prices: JsonNode, fetchedAt: Instant): List<ProviderPrice> =
        listOf(CardVariants.NORMAL to "usd", CardVariants.FOIL to "usd_foil").mapNotNull { (variant, field) ->
            PriceCents.parse(prices.path(field))?.let {
                ProviderPrice(variant, it, null, fetchedAt, PriceSource.TCGPLAYER, Currency.USD)
            }
        }

    /** Lorcast has no finishes field. A card sold in a finish has a price for it, whatever the currency. */
    private fun variants(prices: JsonNode): Set<String> {
        val variants = buildSet {
            if (prices.hasNonNull("usd")) add(CardVariants.NORMAL)
            if (prices.hasNonNull("usd_foil")) add(CardVariants.FOIL)
        }
        return variants.ifEmpty { setOf(CardVariants.NORMAL) }
    }
}
