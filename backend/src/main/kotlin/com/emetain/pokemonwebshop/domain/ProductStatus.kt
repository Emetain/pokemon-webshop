package com.emetain.pokemonwebshop.domain

/** Only [ACTIVE] products are in the shop. [ARCHIVED] ones stay stored for old orders and the stock log. */
enum class ProductStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED,
}
