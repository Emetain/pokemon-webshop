package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceSource
import com.emetain.pokemonwebshop.service.CatalogText
import com.fasterxml.jackson.databind.JsonNode
import java.time.ZoneOffset

/**
 * Maps the OPTCG API to our model. The API has no release dates, so the order of [OptcgLists.sets] decides which
 * set is the newest. It doesn't say what `market_price` is, but it matches TCGplayer's market price in dollars
 * (checked on 86 cards). `inventory_price` matches nothing we could name, so it isn't used.
 */
object OptcgMapper {

    const val PROMO_CODE = "P"
    const val DON_CODE = "DON"
    private const val PROMO_SERIES = "PROMO"
    private const val DON_SERIES = "DON"
    private const val PROMO_NAME = "One Piece Promotion Cards"
    private const val DON_NAME = "DON!! Cards"
    private const val DECK_PREFIX = "ST"

    // One card has "Character" here, so the field can't be trusted blindly.
    private val COLORS = setOf("Red", "Green", "Blue", "Purple", "Black", "Yellow")

    fun sets(lists: OptcgLists): List<ProviderSet> {
        val boosterNames = lists.sets.associate { it.requireText("set_id") to it.requireText("set_name") }
        val deckNames = lists.decks.associate { it.requireText("structure_deck_id") to it.requireText("structure_deck_name") }
        val rowsBySet = (lists.setCards + lists.deckCards).groupBy { it.requireText("set_id") }

        // Some sets only exist through their cards: EB-05 and ST-29 are in neither list.
        val unlisted = rowsBySet.keys - boosterNames.keys - deckNames.keys
        val (unlistedDecks, unlistedBoosters) = unlisted.sortedBy(CatalogText::sortKey).partition { it.startsWith(DECK_PREFIX) }

        // Oldest first, and a higher sort order means newer. Decks go below the boosters, so that the main
        // series come first on the page. Deck numbers run in release order, so they can sit between each other.
        val codes = (deckNames.keys + unlistedDecks).sortedBy(CatalogText::sortKey) +
            boostersInOrder(boosterNames.keys.toList(), unlistedBoosters)

        val sets = codes.map { code ->
            val rows = rowsBySet[code].orEmpty()
            val name = boosterNames[code] ?: deckNames[code] ?: mostCommonName(rows)
            set(code, name, seriesOf(code), rows.map(::card))
        }
        val specials = listOf(
            set(DON_CODE, DON_NAME, DON_SERIES, lists.donCards.map(::donCard)),
            set(PROMO_CODE, PROMO_NAME, PROMO_SERIES, lists.promoCards.map(::card)),
        )
        return (specials + sets).mapIndexed { index, set -> set.copy(sortOrder = index) }
    }

    /** Nothing says when an unlisted booster came out, so it goes right after the last set of its own series. */
    private fun boostersInOrder(listed: List<String>, unlisted: List<String>): List<String> {
        val ordered = listed.toMutableList()
        for (code in unlisted) {
            ordered.add(ordered.indexOfLast { seriesOf(it) == seriesOf(code) } + 1, code)
        }
        return ordered
    }

    private fun set(code: String, name: String, series: String, rows: List<ProviderCard>): ProviderSet {
        // A few rows are exact copies of each other.
        val cards = rows.distinctBy { it.externalId }
        return ProviderSet(
            externalId = code,
            code = code,
            name = name,
            series = series,
            releaseDate = null,
            cardCount = cards.size,
            logoUrl = null,
            symbolUrl = null,
            sortOrder = 0,
            cards = cards,
        )
    }

    private fun card(json: JsonNode) = card(json, number = json.requireText("card_set_id"))

    private fun donCard(json: JsonNode) = card(json, number = json.requireText("card_image_id"))

    /** Ids aren't unique on their own: the same image id occurs in several sets, and even twice within one. */
    private fun card(json: JsonNode, number: String): ProviderCard {
        val name = json.requireText("card_name")
        val image = json.text("card_image")
        return ProviderCard(
            externalId = listOf(json.text("set_id").orEmpty(), json.requireText("card_image_id"), name).joinToString("|"),
            number = number,
            name = name,
            rarity = json.text("rarity"),
            cardType = json.text("card_type"),
            category = json.text("card_type"),
            imageSmallUrl = image,
            imageLargeUrl = image,
            variants = setOf(CardVariants.NORMAL),
            // Cards of two colours say "Green Red".
            colors = json.text("card_color")?.split(' ')?.filter { it in COLORS }?.toSet().orEmpty(),
            prices = listOfNotNull(price(json)),
        )
    }

    /** Every card has its own date, and some are a year old. Without a date we can't say how old a price is. */
    private fun price(json: JsonNode): ProviderPrice? {
        val cents = PriceCents.parse(json.path("market_price")) ?: return null
        val scrapedAt = json.date("date_scraped")?.atStartOfDay(ZoneOffset.UTC)?.toInstant() ?: return null
        return ProviderPrice(CardVariants.NORMAL, cents, null, scrapedAt, PriceSource.TCGPLAYER, Currency.USD)
    }

    private fun seriesOf(code: String): String = code.takeWhile(Char::isLetter)

    // Cards of one set can disagree: two cards of ST-29 carry the name of the booster they also appear in.
    private fun mostCommonName(rows: List<JsonNode>): String =
        rows.mapNotNull { it.text("set_name") }.groupingBy { it }.eachCount().maxBy { it.value }.key
}

/** The answers of the six OPTCG list endpoints. */
data class OptcgLists(
    val sets: List<JsonNode>,
    val decks: List<JsonNode>,
    val setCards: List<JsonNode>,
    val deckCards: List<JsonNode>,
    val promoCards: List<JsonNode>,
    val donCards: List<JsonNode>,
)
