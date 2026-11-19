package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.CardmarketLink
import com.emetain.pokemonwebshop.domain.CardmarketSingle
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.service.provider.ProviderCard
import com.emetain.pokemonwebshop.service.provider.ProviderPrice
import com.emetain.pokemonwebshop.service.provider.TcgdexMapper
import kotlin.math.abs
import kotlin.math.ln

/** What the shop decided about one card's Cardmarket product, and the prices that follow from it. */
data class LinkDecision(val link: CardmarketLink?, val productId: Long?, val prices: List<ProviderPrice>)

/** A link an admin set by hand. [productId] null means "no Cardmarket product fits". */
data class ManualLink(val productId: Long?)

/**
 * Links Pokémon cards to their own Cardmarket product (phase 11). TCGdex links several prints of a card to one
 * product: in Delta Reign, Mega Rayquaza ex 084, 138 and 141 all carry product 918912 and all showed €3.15. Checking
 * 11,944 cards showed that a shared product often isn't even the same card: two different Squirtles of Expedition, or
 * Lapras and Lapras VMAX of Sword & Shield. Cardmarket's own files have a product per print, named after the card
 * with its attacks, but no card number. So:
 *
 * 1. A product only one card of the set has stays that card's ([CardmarketLink.SOURCE]), unless its price and the
 *    card's TCGplayer price are worlds apart ([PriceAgreement.sourceAgrees]).
 * 2. The cards that share a product are taken together by name. Their candidates are the products with that name in
 *    the shared products' expansion that no other card of the set has. When there are as many as there are cards,
 *    they go in order: lowest product id to lowest card number ([CardmarketLink.MATCHED]).
 * 3. When the counts differ, or a link of that order fails the price check ([PriceAgreement.agrees]), each card gets
 *    the free candidate whose price is closest to its TCGplayer price, as long as the two agree.
 * 4. A card that ends up without a product is [CardmarketLink.DOUBTFUL] and shows its TCGplayer price, never the
 *    shared, wrong one. The admin can pick its product by hand ([CardmarketLink.MANUAL]), and a sync keeps that.
 */
object CardmarketLinks {

    private val LEVEL = Regex("\\s+lv\\.?\\s*\\d+$", RegexOption.IGNORE_CASE)

    /**
     * [singles] holds at least the products the cards point at and the other products of their expansions. Returns a
     * decision for every card.
     */
    fun decide(
        cards: List<ProviderCard>,
        manual: Map<String, ManualLink>,
        singles: Map<Long, CardmarketSingle>,
        rates: ExchangeRates,
    ): Map<String, LinkDecision> {
        val automatic = cards.filter { it.externalId !in manual && it.cardmarketProductId != null }
        val groups = automatic.groupBy { it.cardmarketProductId!! }
        val taken = groups.filterValues { it.size == 1 }.keys + manual.values.mapNotNull { it.productId }

        val decisions = mutableMapOf<String, LinkDecision>()
        manual.forEach { (externalId, link) ->
            cards.find { it.externalId == externalId }?.let { decisions[externalId] = decideManual(it, link, singles) }
        }
        groups.filter { it.value.size == 1 }.forEach { (productId, group) ->
            val card = group.single()
            decisions[card.externalId] = decideSource(card, productId, singles, rates)
        }
        groups.values.filter { it.size > 1 }.flatten()
            .groupBy { CatalogText.nameSearch(it.name) }
            .forEach { (name, sharing) -> decisions.putAll(decideShared(name, sharing, singles, taken, rates)) }

        return cards.associate { card ->
            // No product at all: nothing to doubt. TCGdex only has Cardmarket prices with a product, so this is rare.
            card.externalId to (decisions[card.externalId] ?: LinkDecision(null, null, card.prices.ifEmpty { card.fallbackPrices }))
        }
    }

    private fun decideSource(
        card: ProviderCard,
        productId: Long,
        singles: Map<Long, CardmarketSingle>,
        rates: ExchangeRates,
    ): LinkDecision {
        // Without today's file, TCGdex's own copy of the same product's prices will do.
        val prices = singles[productId]?.let { pricesOf(card, it) } ?: card.prices
        return if (PriceAgreement.sourceAgrees(prices, card.fallbackPrices, rates)) {
            LinkDecision(CardmarketLink.SOURCE, productId, prices.ifEmpty { card.fallbackPrices })
        } else {
            LinkDecision(CardmarketLink.DOUBTFUL, productId, card.fallbackPrices)
        }
    }

    private fun decideShared(
        name: String,
        sharing: List<ProviderCard>,
        singles: Map<Long, CardmarketSingle>,
        taken: Set<Long>,
        rates: ExchangeRates,
    ): Map<String, LinkDecision> {
        val expansions = sharing.mapNotNull { singles[it.cardmarketProductId]?.expansionId }.toSet()
        val candidates = singles.values
            .filter { it.expansionId in expansions && it.productId !in taken && baseName(it) == name }
            .sortedBy { it.productId }
        val ordered = sharing.sortedWith(compareBy<ProviderCard>({ CatalogText.sortKey(it.number) }, { it.externalId }))

        val inOrder = if (candidates.size == ordered.size) ordered.zip(candidates) else null
        val matches = inOrder
            ?.takeIf { pairs -> pairs.all { (card, single) -> PriceAgreement.agrees(pricesOf(card, single), card.fallbackPrices, rates) } }
            ?.associate { (card, single) -> card.externalId to single }
            ?: byPrice(ordered, candidates, rates)

        return ordered.associate { card ->
            val single = matches[card.externalId]
            // A doubtful card keeps the source's product for the admin page: it tells which expansion to look in.
            card.externalId to if (single == null) {
                LinkDecision(CardmarketLink.DOUBTFUL, card.cardmarketProductId, card.fallbackPrices)
            } else {
                LinkDecision(CardmarketLink.MATCHED, single.productId, pricesOf(card, single).ifEmpty { card.fallbackPrices })
            }
        }
    }

    /** Each card gets the free candidate closest to its TCGplayer price, the closest pairs first. */
    private fun byPrice(
        cards: List<ProviderCard>,
        candidates: List<CardmarketSingle>,
        rates: ExchangeRates,
    ): Map<String, CardmarketSingle> {
        val pairs = cards.flatMap { card ->
            candidates.mapNotNull { single ->
                PriceAgreement.distance(pricesOf(card, single), card.fallbackPrices, rates)?.let { Triple(it, card, single) }
            }
        }.sortedWith(compareBy<Triple<Double, ProviderCard, CardmarketSingle>>({ it.first }, { CatalogText.sortKey(it.second.number) }, { it.third.productId }))

        val matches = mutableMapOf<String, CardmarketSingle>()
        val used = mutableSetOf<Long>()
        for ((_, card, single) in pairs) {
            if (card.externalId in matches || single.productId in used) continue
            matches[card.externalId] = single
            used += single.productId
        }
        return matches
    }

    private fun decideManual(card: ProviderCard, link: ManualLink, singles: Map<Long, CardmarketSingle>): LinkDecision {
        val prices = link.productId?.let(singles::get)?.let { pricesOf(card, it) }.orEmpty()
        return LinkDecision(CardmarketLink.MANUAL, link.productId, prices.ifEmpty { card.fallbackPrices })
    }

    /** "Sceptile Lv.57 [Green Breath | Leaf Blast]" is the card "Sceptile". */
    fun baseName(single: CardmarketSingle): String =
        CatalogText.nameSearch(single.name.substringBefore(" [").replace(LEVEL, ""))

    /** A product's two columns for the card's variants, plus TCGdex's 1st edition price, which has its own product. */
    fun pricesOf(card: ProviderCard, single: CardmarketSingle): List<ProviderPrice> =
        pricesOf(card.variants, single) + card.prices.filter { it.variant == CardVariants.FIRST_EDITION }

    fun pricesOf(variants: Set<String>, single: CardmarketSingle): List<ProviderPrice> =
        TcgdexMapper.cardmarketPrices(
            variants,
            TcgdexMapper.Amounts(single.trendCents, single.averageCents),
            TcgdexMapper.Amounts(single.foilTrendCents, single.foilAverageCents),
            single.updatedAt,
        )
}

/**
 * Whether a Cardmarket price and a TCGplayer price can be the same print. Measured on the 11,944 cards of the 76 sets
 * with shared prices (2026-11-19, see `docs/phases/phase-11.md`): even for links that are right, the two markets
 * differ more than 3 times for 6.9% of the cards, mostly vintage cards with few sales. A wrong print differs far
 * more: €2.93 against $277 for the Rayquaza above. So a product TCGdex gave to one card only is only doubted at 10
 * times, and a match the shop made itself at 3 times.
 */
object PriceAgreement {

    /** Below this, in euro cents, both prices are too small to tell prints apart. */
    const val FLOOR_CENTS = 200L

    /** For a match the shop made: the higher price may be at most this many times the lower one. */
    const val MAX_RATIO = 3.0

    /** For TCGdex's own product, which no other card of the set has. */
    const val SOURCE_FLOOR_CENTS = 500L
    const val SOURCE_MAX_RATIO = 10.0

    fun agrees(cardmarket: List<ProviderPrice>, tcgplayer: List<ProviderPrice>, rates: ExchangeRates): Boolean =
        compare(cardmarket, tcgplayer, rates)?.let { (a, b) -> agrees(a, b, FLOOR_CENTS, MAX_RATIO) } ?: true

    fun sourceAgrees(cardmarket: List<ProviderPrice>, tcgplayer: List<ProviderPrice>, rates: ExchangeRates): Boolean =
        compare(cardmarket, tcgplayer, rates)?.let { (a, b) -> agrees(a, b, SOURCE_FLOOR_CENTS, SOURCE_MAX_RATIO) } ?: true

    /** How far apart the two are, for prices that agree; null when they don't or there's nothing to compare. */
    fun distance(cardmarket: List<ProviderPrice>, tcgplayer: List<ProviderPrice>, rates: ExchangeRates): Double? {
        val (a, b) = compare(cardmarket, tcgplayer, rates) ?: return null
        if (!agrees(a, b, FLOOR_CENTS, MAX_RATIO)) return null
        return abs(ln(maxOf(a, 1L).toDouble() / maxOf(b, 1L).toDouble()))
    }

    fun agrees(aCents: Long, bCents: Long, floorCents: Long = FLOOR_CENTS, maxRatio: Double = MAX_RATIO): Boolean {
        val high = maxOf(aCents, bCents)
        val low = minOf(aCents, bCents)
        if (high < floorCents) return true
        if (low <= 0) return false
        return high.toDouble() / low.toDouble() <= maxRatio
    }

    /** Both trend prices in euro cents, for the first variant both have one for. */
    private fun compare(cardmarket: List<ProviderPrice>, tcgplayer: List<ProviderPrice>, rates: ExchangeRates): Pair<Long, Long>? {
        val variant = CardVariants.DISPLAY_ORDER.firstOrNull { variant ->
            cardmarket.any { it.variant == variant && it.trendCents != null } &&
                tcgplayer.any { it.variant == variant && it.trendCents != null }
        } ?: return null
        val market = cardmarket.first { it.variant == variant && it.trendCents != null }
        val player = tcgplayer.first { it.variant == variant && it.trendCents != null }
        val marketEuros = rates.convertCents(market.trendCents!!, market.currency, Currency.EUR) ?: return null
        val playerEuros = rates.convertCents(player.trendCents!!, player.currency, Currency.EUR) ?: return null
        return marketEuros to playerEuros
    }
}
