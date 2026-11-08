package com.emetain.pokemonwebshop.domain

/**
 * Why stock changed. [RESERVATION] and [RELEASE] change the reserved quantity, all others the quantity on hand.
 * [PURCHASE] is stock the shop bought in, [SALE] stock a customer bought.
 */
enum class StockReason(val changesReserved: Boolean = false) {
    PURCHASE,
    SALE,
    RETURN,
    CORRECTION,
    DROP_ALLOCATION,
    RESERVATION(changesReserved = true),
    RELEASE(changesReserved = true),
}
