package com.emetain.pokemonwebshop.domain

/** The condition of a card, from best to worst. Uses the scale of Cardmarket, which collectors know. */
enum class CardCondition {
    MINT,
    NEAR_MINT,
    EXCELLENT,
    GOOD,
    LIGHT_PLAYED,
    PLAYED,
    POOR,
}
