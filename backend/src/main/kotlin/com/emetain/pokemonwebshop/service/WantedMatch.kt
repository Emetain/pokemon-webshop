package com.emetain.pokemonwebshop.service

/**
 * How a wanted card that's for sale compares with what the customer wants (2026-11-23, the owner's friend's idea): the
 * colour of the frame around it on the home page. Only the customer's own maximum counts, never the market price:
 * the shop doesn't mark its own prices as too high.
 */
enum class WantedMatch {
    /** Red: dearer than the maximum the customer set. Goes before [OWNED]: the price is what stops them. */
    ABOVE_MAX,

    /** Blue: already in the collection, and still on the wishlist, so they want more. */
    OWNED,

    /** Green: not in the collection yet, and within the maximum, or no maximum is set. */
    WITHIN,
    ;

    companion object {
        fun of(priceCents: Long?, maxPriceCents: Long?, owned: Boolean): WantedMatch = when {
            maxPriceCents != null && priceCents != null && priceCents > maxPriceCents -> ABOVE_MAX
            owned -> OWNED
            else -> WITHIN
        }
    }
}
