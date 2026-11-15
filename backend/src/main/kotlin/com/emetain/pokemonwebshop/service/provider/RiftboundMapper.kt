package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceSource
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** One TCGplayer group of Riftbound with its products and prices, as tcgcsv.com serves them. */
data class RiftboundGroup(val group: JsonNode, val products: List<JsonNode>, val prices: List<JsonNode>)

/**
 * Maps Riftbound from TCGplayer (through tcgcsv.com) to our model. A group is a set. Its products with a `Number`
 * are cards, the rest are sealed products. Prices are TCGplayer's market price in dollars, per printing: `Normal`
 * or `Foil`. Groups without cards, such as bundles, stay out.
 */
object RiftboundMapper {

    const val MAIN_SERIES = "main"
    const val SPECIAL_SERIES = "special"
    const val PROMO_SERIES = "promo"

    /** The main sets have a three letter code and hundreds of cards. Proving Grounds (OGS) has 24. */
    private const val MAIN_SET_MIN_CARDS = 100
    private val MAIN_CODE = Regex("[A-Z]{3}")
    private val NOT_IN_CODE = Regex("[^A-Za-z0-9-]+")

    private const val SMALL_IMAGE = "_200w.jpg"
    private const val LARGE_IMAGE = "_in_1000x1000.jpg"

    fun sets(groups: List<RiftboundGroup>, fetchedAt: Instant): List<ProviderSet> =
        groups
            .mapNotNull { set(it, fetchedAt) }
            .sortedWith(compareBy<ProviderSet, LocalDate?>(nullsLast()) { it.releaseDate }.thenBy { it.name })
            .mapIndexed { index, set -> set.copy(sortOrder = index) }

    private fun set(source: RiftboundGroup, fetchedAt: Instant): ProviderSet? {
        val group = source.group
        val pricesByProduct = source.prices.groupBy { it.path("productId").asLong() }
        val cards = source.products
            .filter { number(it) != null }
            .map { card(it, pricesByProduct[it.path("productId").asLong()].orEmpty(), fetchedAt) }
            .distinctBy { it.externalId }
        if (cards.isEmpty()) return null
        val name = group.requireText("name")
        val code = group.text("abbreviation")?.let(::code)?.ifEmpty { null } ?: group.requireText("groupId")
        return ProviderSet(
            externalId = group.requireText("groupId"),
            code = code,
            name = name,
            series = seriesOf(name, code, cards.size),
            releaseDate = publishedOn(group),
            cardCount = cards.size,
            logoUrl = null,
            symbolUrl = null,
            sortOrder = 0,
            cards = cards,
        )
    }

    /** The code goes in URLs: "RXK/DA" (the K/DA collection) becomes "RXK-DA". */
    fun code(abbreviation: String): String = abbreviation.trim().replace(NOT_IN_CODE, "-").trim('-')

    fun seriesOf(name: String, code: String, cardCount: Int): String = when {
        "promo" in name.lowercase() -> PROMO_SERIES
        MAIN_CODE.matches(code) && cardCount >= MAIN_SET_MIN_CARDS -> MAIN_SERIES
        else -> SPECIAL_SERIES
    }

    fun card(json: JsonNode, prices: List<JsonNode>, fetchedAt: Instant): ProviderCard {
        val image = json.text("imageUrl")
        val cardType = extended(json, "Card Type")?.split(';')?.map(String::trim)?.filter(String::isNotEmpty)
        val providerPrices = prices.mapNotNull { price(it, fetchedAt) }
        return ProviderCard(
            externalId = json.requireText("productId"),
            number = requireNotNull(number(json)) { "Card without a number: $json" },
            name = json.requireText("name"),
            rarity = extended(json, "Rarity"),
            cardType = cardType?.joinToString(" - ")?.ifBlank { null },
            category = cardType?.firstOrNull(),
            imageSmallUrl = image,
            imageLargeUrl = image?.takeIf { it.endsWith(SMALL_IMAGE) }?.replace(SMALL_IMAGE, LARGE_IMAGE) ?: image,
            variants = variants(prices),
            colors = extended(json, "Domain")?.split(';')?.map(String::trim)?.filter(String::isNotEmpty)?.toSet().orEmpty(),
            prices = providerPrices,
        )
    }

    /** "136/167" is card 136. "T1A 001/005" and "126b/298" keep their prefix and letter. */
    fun number(json: JsonNode): String? = extended(json, "Number")?.substringBefore('/')?.trim()?.ifEmpty { null }

    private fun variants(prices: List<JsonNode>): Set<String> =
        prices.mapNotNull { variantOf(it.text("subTypeName")) }.toSet().ifEmpty { setOf(CardVariants.NORMAL) }

    private fun price(json: JsonNode, fetchedAt: Instant): ProviderPrice? {
        val variant = variantOf(json.text("subTypeName")) ?: return null
        val market = PriceCents.parse(json.path("marketPrice")) ?: return null
        return ProviderPrice(variant, market, null, fetchedAt, PriceSource.TCGPLAYER, Currency.USD)
    }

    private fun variantOf(subType: String?): String? = when (subType?.lowercase()) {
        "normal" -> CardVariants.NORMAL
        "foil" -> CardVariants.FOIL
        else -> null
    }

    private fun extended(json: JsonNode, name: String): String? =
        json.path("extendedData").firstOrNull { it.text("name") == name }?.text("value")?.trim()

    private fun publishedOn(group: JsonNode): LocalDate? =
        group.text("publishedOn")?.take(DATE_LENGTH)?.let {
            try {
                LocalDate.parse(it)
            } catch (exception: DateTimeParseException) {
                null
            }
        }

    private const val DATE_LENGTH = 10
}
