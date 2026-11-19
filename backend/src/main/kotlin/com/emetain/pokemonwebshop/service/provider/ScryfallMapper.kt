package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceSource
import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * Maps Scryfall JSON to our model. `prices.eur` and `prices.eur_foil` come from Cardmarket, for the product in
 * `cardmarket_id`; `prices.usd`, `usd_foil` and `usd_etched` from TCGplayer. Since phase 11 the shop checks that product
 * itself, because Scryfall gives some prints the same one ([com.emetain.pokemonwebshop.service.CardmarketLinks]).
 */
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
        val variants = json.path("finishes").mapNotNull { VARIANTS_BY_FINISH[it.asString()] }.toSet()
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
            cardmarketProductId = json.path("cardmarket_id").asLong(0).takeIf { it > 0 },
            fallbackPrices = tcgplayerPrices(json.path("prices"), variants, pricesUpdatedAt),
        )
    }

    /**
     * A Cardmarket product's two columns for a Magic card: the foil column is the foil print. Etched foils have no
     * column of their own there, so they keep their TCGplayer price.
     */
    fun cardmarketPrices(variants: Set<String>, base: TcgdexMapper.Amounts, foil: TcgdexMapper.Amounts, updatedAt: Instant): List<ProviderPrice> =
        listOf(CardVariants.NORMAL to base, CardVariants.FOIL to foil)
            .filter { (variant, amounts) -> variant in variants && !amounts.isEmpty }
            .map { (variant, amounts) -> amounts.price(variant, updatedAt) }

    /** The types of the front face: "Legendary Creature — Elf // Land" counts as a creature. */
    private fun category(typeLine: String?): String? {
        val types = typeLine?.substringBefore(" // ")?.substringBefore(" — ")?.split(' ') ?: return null
        return CATEGORIES.firstOrNull { it in types }
    }

    /** Double-faced cards have their colours on the faces, like their images. */
    private fun colors(json: JsonNode): Set<String> {
        val codes = json.path("colors").takeIf { it.isArray } ?: json.path("card_faces").path(0).path("colors")
        return codes.mapNotNull { COLOR_NAMES[it.asString()] }.toSet().ifEmpty { setOf(COLORLESS) }
    }

    private fun tcgplayerPrices(prices: JsonNode, variants: Set<String>, updatedAt: Instant): List<ProviderPrice> =
        listOf(CardVariants.NORMAL to "usd", CardVariants.FOIL to "usd_foil", CardVariants.ETCHED to "usd_etched")
            .filter { (variant, _) -> variant in variants }
            .mapNotNull { (variant, field) ->
                PriceCents.parse(prices.path(field))
                    ?.let { ProviderPrice(variant, it, null, updatedAt, PriceSource.TCGPLAYER, Currency.USD) }
            }

    private fun prices(prices: JsonNode, variants: Set<String>, updatedAt: Instant): List<ProviderPrice> =
        listOf(CardVariants.NORMAL to "eur", CardVariants.FOIL to "eur_foil")
            .filter { (variant, _) -> variant in variants }
            .mapNotNull { (variant, field) ->
                PriceCents.parse(prices.path(field))?.let { ProviderPrice(variant, it, null, updatedAt) }
            }
}
