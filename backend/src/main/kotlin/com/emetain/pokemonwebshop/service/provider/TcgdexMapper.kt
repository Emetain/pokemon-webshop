package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.CardVariants
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.time.format.DateTimeParseException

/** Maps TCGdex JSON to our model. The price rules come from checking real cards, see [prices]. */
object TcgdexMapper {

    private const val FIRST_EDITION_STAMP = "1st-edition"
    private const val JUMBO_SIZE = "jumbo"
    private const val NO_RARITY = "None"
    private const val ENERGY_CATEGORY = "Energy"
    private const val ENERGY_SUFFIX = " Energy"

    private val ENERGY_TYPES = setOf(
        "Grass", "Fire", "Water", "Lightning", "Psychic", "Fighting", "Darkness", "Metal", "Fairy", "Dragon", "Colorless",
    )

    private val VARIANT_FIELDS = listOf(
        CardVariants.NORMAL,
        CardVariants.HOLO,
        CardVariants.REVERSE,
        CardVariants.FIRST_EDITION,
        CardVariants.W_PROMO,
    )

    fun set(json: JsonNode, sortOrder: Int, cards: List<ProviderCard>, failedCards: Int): ProviderSet {
        val id = json.requireText("id")
        return ProviderSet(
            externalId = id,
            code = id,
            name = json.requireText("name"),
            series = json.path("serie").requireText("name"),
            releaseDate = json.date("releaseDate"),
            cardCount = json.path("cardCount").path("total").asInt(cards.size + failedCards),
            logoUrl = json.text("logo")?.let { "$it.webp" },
            symbolUrl = json.text("symbol")?.let { "$it.webp" },
            sortOrder = sortOrder,
            cards = cards,
            failedCards = failedCards,
        )
    }

    fun card(json: JsonNode): ProviderCard {
        val image = json.text("image")
        val variants = VARIANT_FIELDS.filter { json.path("variants").path(it).asBoolean(false) }.toSet()
        return ProviderCard(
            externalId = json.requireText("id"),
            number = json.requireText("localId"),
            name = json.requireText("name"),
            rarity = json.text("rarity")?.takeIf { it != NO_RARITY },
            cardType = json.text("category"),
            category = json.text("category"),
            imageSmallUrl = image?.let { "$it/low.webp" },
            imageLargeUrl = image?.let { "$it/high.webp" },
            variants = variants,
            colors = colors(json),
            dexNumbers = json.path("dexId").filter { it.isInt }.map { it.asInt() }.toSet(),
            prices = prices(json, variants),
        )
    }

    /** Pokémon list their type. Basic energy cards only have it in their name: "Fire Energy". */
    private fun colors(card: JsonNode): Set<String> {
        val types = card.path("types").map { it.asText() }.toSet()
        if (types.isNotEmpty() || card.text("category") != ENERGY_CATEGORY) return types
        return setOfNotNull(card.text("name")?.removeSuffix(ENERGY_SUFFIX)?.takeIf { it in ENERGY_TYPES })
    }

    /**
     * Cardmarket has two price columns per product, and TCGdex passes them on as `trend` and `trend-holo`.
     * The second one means "the foil printing" of that product, which is the reverse holo when a card has one.
     * Reading it as "the holo price" shows wrong prices: Charizard `base1-4` has a `trend-holo` although no
     * reverse exists. So who gets which column depends on the variants the card really has.
     */
    private fun prices(card: JsonNode, variants: Set<String>): List<ProviderPrice> {
        val market = card.path("pricing").path("cardmarket")
        val updatedAt = instant(market.text("updated")) ?: return emptyList()

        val base = Amounts(PriceCents.parse(market.path("trend")), PriceCents.parse(market.path("avg")))
        val foil = Amounts(PriceCents.parse(market.path("trend-holo")), PriceCents.parse(market.path("avg-holo")))

        val hasNormal = CardVariants.NORMAL in variants
        val hasHolo = CardVariants.HOLO in variants
        val hasReverse = CardVariants.REVERSE in variants

        val amounts = buildMap<String, Amounts> {
            if (hasNormal) put(CardVariants.NORMAL, base) else if (hasHolo) put(CardVariants.HOLO, base)

            when {
                hasReverse && (hasNormal || hasHolo) -> put(CardVariants.REVERSE, foil)
                hasReverse -> put(CardVariants.REVERSE, if (foil.isEmpty) base else foil)
                hasNormal && hasHolo -> put(CardVariants.HOLO, foil)
            }

            if (CardVariants.FIRST_EDITION in variants) {
                firstEdition(card, market)?.let { put(CardVariants.FIRST_EDITION, it) }
            }
        }

        return amounts.filterValues { !it.isEmpty }
            .map { (variant, amount) -> ProviderPrice(variant, amount.trend, amount.average, updatedAt) }
    }

    /** The 1st edition is only a real price when Cardmarket lists it as its own product. */
    private fun firstEdition(card: JsonNode, market: JsonNode): Amounts? {
        val productId = market.text("idProduct")
        return card.path("variants_detailed")
            .filter { entry ->
                entry.path("stamp").any { it.asText() == FIRST_EDITION_STAMP } && entry.text("size") != JUMBO_SIZE
            }
            .map { it.path("pricing").path("cardmarket") }
            .firstOrNull { it.text("idProduct").let { id -> id != null && id != productId } }
            ?.let { Amounts(PriceCents.parse(it.path("trend")), PriceCents.parse(it.path("avg"))) }
    }

    private fun instant(text: String?): Instant? =
        text?.let {
            try {
                Instant.parse(it)
            } catch (exception: DateTimeParseException) {
                null
            }
        }

    private data class Amounts(val trend: Long?, val average: Long?) {
        val isEmpty: Boolean get() = trend == null && average == null
    }
}
