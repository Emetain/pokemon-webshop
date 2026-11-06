package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.CardVariants
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

/** Maps Scryfall JSON to our model. `prices.eur` and `prices.eur_foil` come from Cardmarket. */
object ScryfallMapper {

    private val VARIANTS_BY_FINISH = mapOf(
        "nonfoil" to CardVariants.NORMAL,
        "foil" to CardVariants.FOIL,
        "etched" to CardVariants.ETCHED,
    )

    private val COLOR_NAMES = mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green")
    private const val COLORLESS = "Colorless"

    // A card can be several of these, such as "Artifact Creature". The first match is what people call it.
    private val CATEGORIES = listOf("Creature", "Planeswalker", "Battle", "Instant", "Sorcery", "Artifact", "Enchantment", "Land")

    fun isDigital(json: JsonNode): Boolean = json.path("digital").asBoolean(false)

    fun set(json: JsonNode, sortOrder: Int, cards: List<ProviderCard>) = ProviderSet(
        externalId = json.requireText("id"),
        code = json.requireText("code"),
        name = json.requireText("name"),
        series = json.requireText("set_type"),
        releaseDate = json.date("released_at"),
        cardCount = json.path("card_count").asInt(cards.size),
        logoUrl = null,
        symbolUrl = json.text("icon_svg_uri"),
        sortOrder = sortOrder,
        cards = cards,
    )

    fun card(json: JsonNode, pricesUpdatedAt: Instant): ProviderCard {
        val variants = json.path("finishes").mapNotNull { VARIANTS_BY_FINISH[it.asText()] }.toSet()
        // Double-faced cards have their images on the faces. The front is the one to show.
        val images = json.path("image_uris").takeIf { it.isObject }
            ?: json.path("card_faces").path(0).path("image_uris")

        return ProviderCard(
            externalId = json.requireText("id"),
            number = json.requireText("collector_number"),
            name = json.requireText("name"),
            rarity = json.text("rarity")?.replaceFirstChar(Char::uppercase),
            cardType = json.text("type_line"),
            category = category(json.text("type_line")),
            imageSmallUrl = images.text("normal"),
            imageLargeUrl = images.text("large"),
            variants = variants,
            colors = colors(json),
            prices = prices(json.path("prices"), variants, pricesUpdatedAt),
        )
    }

    /** The types of the front face: "Legendary Creature — Elf // Land" counts as a creature. */
    private fun category(typeLine: String?): String? {
        val types = typeLine?.substringBefore(" // ")?.substringBefore(" — ")?.split(' ') ?: return null
        return CATEGORIES.firstOrNull { it in types }
    }

    /** Double-faced cards have their colours on the faces, like their images. */
    private fun colors(json: JsonNode): Set<String> {
        val codes = json.path("colors").takeIf { it.isArray } ?: json.path("card_faces").path(0).path("colors")
        return codes.mapNotNull { COLOR_NAMES[it.asText()] }.toSet().ifEmpty { setOf(COLORLESS) }
    }

    private fun prices(prices: JsonNode, variants: Set<String>, updatedAt: Instant): List<ProviderPrice> =
        listOf(CardVariants.NORMAL to "eur", CardVariants.FOIL to "eur_foil")
            .filter { (variant, _) -> variant in variants }
            .mapNotNull { (variant, field) ->
                PriceCents.parse(prices.path(field))?.let { ProviderPrice(variant, it, null, updatedAt) }
            }
}
