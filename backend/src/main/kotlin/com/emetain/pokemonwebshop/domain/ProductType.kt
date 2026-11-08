package com.emetain.pokemonwebshop.domain

/** What kind of product something is. [sealed] products come from a set and stay closed, [forCard] ones are one card. */
enum class ProductType(val sealed: Boolean = false, val forCard: Boolean = false) {
    BOOSTER_BOX(sealed = true),
    ELITE_TRAINER_BOX(sealed = true),
    BOOSTER_BUNDLE(sealed = true),
    BOOSTER_PACK(sealed = true),
    SLEEVED_BOOSTER(sealed = true),
    BLISTER(sealed = true),
    TIN(sealed = true),
    COLLECTION_BOX(sealed = true),
    STARTER_DECK(sealed = true),
    SINGLE(forCard = true),
    GRADED_SLAB(forCard = true),
    BULK,
    MYSTERY_BOX,
    ACCESSORY,
}
