package com.emetain.pokemonwebshop.domain

/**
 * How a card got its Cardmarket product (phase 11). TCGdex links several prints of one card to the same product,
 * so the shop keeps its own link, checked against the card's TCGplayer price.
 */
enum class CardmarketLink {
    /** TCGdex's product, which no other card of the set has, and the prices agree. */
    SOURCE,

    /** TCGdex gave the product to several prints; this one was matched to its own print by number order. */
    MATCHED,

    /** Chosen by an admin. A sync never changes it. The product can be null: "none of them, use TCGplayer". */
    MANUAL,

    /**
     * No product could be trusted: the best candidate's price disagrees with TCGplayer's, or there was no candidate.
     * The card shows its TCGplayer price, and the admin can pick a product by hand. [Card.cardmarketProductId] then
     * holds the product the source gave, which is not used for prices.
     */
    DOUBTFUL,
    ;

    /** Whether the card's prices come from [Card.cardmarketProductId]. */
    val trusted: Boolean get() = this == SOURCE || this == MATCHED || this == MANUAL
}
