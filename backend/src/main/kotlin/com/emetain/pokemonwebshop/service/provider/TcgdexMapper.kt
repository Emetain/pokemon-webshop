package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceSource
import tools.jackson.databind.JsonNode
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

    /** The `variantId` of TCGdex's placeholder variants for a new set, see [variants]. */
    private const val GENERATED_VARIANT = "generated"

    /** TCGplayer's price keys at TCGdex, and the finish each one is. */
    private val TCGPLAYER_FINISHES = mapOf(
        "normal" to CardVariants.NORMAL,
        "unlimited" to CardVariants.NORMAL,
        "holofoil" to CardVariants.HOLO,
        "unlimited-holofoil" to CardVariants.HOLO,
        "reverse-holofoil" to CardVariants.REVERSE,
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
        val variants = variants(json)
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
            cardmarketProductId = json.path("pricing").path("cardmarket").path("idProduct").asLong(0).takeIf { it > 0 },
            fallbackPrices = tcgplayerPrices(json, variants),
        )
    }

    /**
     * The finishes a card is printed in. For a new set TCGdex first gives every card a placeholder: "normal" only,
     * with `variantId: "generated"` in `variants_detailed`. Also for ex cards that only exist as holo, and for commons
     * that also have a reverse holo (found on 2026-11-20 in Delta Reign, `me06`). TCGplayer's prices then already
     * name the real finishes, so those count; without them the rule of modern sets by rarity, see [variantsByRarity].
     */
    fun variants(json: JsonNode): Set<String> {
        val listed = VARIANT_FIELDS.filter { json.path("variants").path(it).asBoolean(false) }.toSet()
        if (!placeholderVariants(json)) return listed
        return tcgplayerVariants(json).ifEmpty { variantsByRarity(json.text("rarity")) ?: listed }
    }

    private fun placeholderVariants(json: JsonNode): Boolean {
        val detailed = json.path("variants_detailed")
        return detailed.isArray && !detailed.isEmpty && detailed.all { it.text("variantId") == GENERATED_VARIANT }
    }

    /** The finishes TCGplayer has a price for. "unlimited" is TCGplayer's name for the normal print of old sets. */
    private fun tcgplayerVariants(json: JsonNode): Set<String> {
        val market = json.path("pricing").path("tcgplayer")
        return TCGPLAYER_FINISHES.filterKeys { market.path(it).isObject }.values.toSet()
    }

    /**
     * The finishes of a card in a modern set (Scarlet & Violet and later) by its rarity, as the owner described them
     * on 2026-11-20: commons and uncommons have a normal and a reverse holo print, rares a holo and a reverse holo,
     * and everything above (ex, illustration rares, special illustration rares, gold) one holo print. Null for a
     * rarity it doesn't know, such as promos.
     */
    fun variantsByRarity(rarity: String?): Set<String>? {
        val name = rarity?.trim()?.lowercase() ?: return null
        return when {
            name == "common" || name == "uncommon" -> setOf(CardVariants.NORMAL, CardVariants.REVERSE)
            name == "rare" -> setOf(CardVariants.HOLO, CardVariants.REVERSE)
            name.contains("rare") -> setOf(CardVariants.HOLO)
            else -> null
        }
    }

    /** Pokémon list their type. Basic energy cards only have it in their name: "Fire Energy". */
    private fun colors(card: JsonNode): Set<String> {
        val types = card.path("types").values().map { it.asString() }.toSet()
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
        val firstEdition = if (CardVariants.FIRST_EDITION in variants) firstEdition(card, market) else null

        return cardmarketPrices(variants, base, foil, updatedAt) +
            listOfNotNull(firstEdition?.takeIf { !it.isEmpty }?.price(CardVariants.FIRST_EDITION, updatedAt))
    }

    /**
     * Who gets which of Cardmarket's two columns of one product, see [prices]. Also used for the shop's own link to
     * a Cardmarket product (phase 11), which has the same two columns. The 1st edition isn't in here: it has a
     * product of its own.
     */
    fun cardmarketPrices(variants: Set<String>, base: Amounts, foil: Amounts, updatedAt: Instant): List<ProviderPrice> {
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
        }

        return amounts.filterValues { !it.isEmpty }.map { (variant, amount) -> amount.price(variant, updatedAt) }
    }

    /**
     * TCGplayer's prices that TCGdex passes on, per print and right where Cardmarket's link is shared (phase 11).
     * TCGplayer names its finishes itself, and a card's variants don't always match them: Mega Rayquaza ex `me06-138`
     * is "normal" at TCGdex and "holofoil" at TCGplayer. So the base price goes to the card's base variant.
     */
    private fun tcgplayerPrices(card: JsonNode, variants: Set<String>): List<ProviderPrice> {
        val market = card.path("pricing").path("tcgplayer")
        val updatedAt = instant(market.text("updated")) ?: return emptyList()

        fun amounts(vararg keys: String): Amounts? =
            keys.asSequence()
                .map { market.path(it) }
                .map { Amounts(PriceCents.parse(it.path("marketPrice")) ?: PriceCents.parse(it.path("midPrice")), null) }
                .firstOrNull { !it.isEmpty }

        val normal = amounts("normal", "unlimited")
        val holo = amounts("holofoil", "unlimited-holofoil")
        val hasNormal = CardVariants.NORMAL in variants
        val hasHolo = CardVariants.HOLO in variants

        val prices = buildMap<String, Amounts?> {
            when {
                hasNormal && hasHolo -> {
                    put(CardVariants.NORMAL, normal)
                    put(CardVariants.HOLO, holo)
                }
                hasHolo -> put(CardVariants.HOLO, holo ?: normal)
                hasNormal || CardVariants.REVERSE !in variants -> put(CardVariants.NORMAL, normal ?: holo)
            }
            if (CardVariants.REVERSE in variants) put(CardVariants.REVERSE, amounts("reverse-holofoil"))
            if (CardVariants.FIRST_EDITION in variants) {
                put(CardVariants.FIRST_EDITION, amounts("1st-edition-holofoil", "1st-edition"))
            }
        }

        return prices.mapNotNull { (variant, amount) ->
            amount?.let { ProviderPrice(variant, it.trend, it.average, updatedAt, PriceSource.TCGPLAYER, Currency.USD) }
        }
    }

    /** The 1st edition is only a real price when Cardmarket lists it as its own product. */
    private fun firstEdition(card: JsonNode, market: JsonNode): Amounts? {
        val productId = market.text("idProduct")
        return card.path("variants_detailed")
            .filter { entry ->
                entry.path("stamp").any { it.asString() == FIRST_EDITION_STAMP } && entry.text("size") != JUMBO_SIZE
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

    /** A trend and an average price in cents, as one column of a marketplace has them. */
    data class Amounts(val trend: Long?, val average: Long?) {
        val isEmpty: Boolean get() = trend == null && average == null

        fun price(variant: String, updatedAt: Instant) = ProviderPrice(variant, trend, average, updatedAt)
    }
}
