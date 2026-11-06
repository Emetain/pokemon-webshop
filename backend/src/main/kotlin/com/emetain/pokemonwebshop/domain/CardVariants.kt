package com.emetain.pokemonwebshop.domain

/** The finishes a card can have. Stored as text, so a new source can add its own. */
object CardVariants {
    const val NORMAL = "normal"
    const val HOLO = "holo"
    const val REVERSE = "reverse"
    const val FIRST_EDITION = "firstEdition"
    const val W_PROMO = "wPromo"
    const val FOIL = "foil"
    const val ETCHED = "etched"

    /** Decides which price represents a card in lists: the most common finish that has one. */
    val DISPLAY_ORDER = listOf(NORMAL, HOLO, REVERSE, FOIL, ETCHED, FIRST_EDITION, W_PROMO)

    /** The price shown for a card in lists, or null when no variant has a trend price. */
    fun displayPrice(prices: List<CardPrice>): CardPrice? =
        prices.filter { it.trendCents != null }.minByOrNull { displayRank(it.variant) }

    fun displaySorted(variants: Collection<String>): List<String> = variants.sortedBy(::displayRank)

    fun displayRank(variant: String): Int =
        DISPLAY_ORDER.indexOf(variant).takeIf { it >= 0 } ?: DISPLAY_ORDER.size
}
