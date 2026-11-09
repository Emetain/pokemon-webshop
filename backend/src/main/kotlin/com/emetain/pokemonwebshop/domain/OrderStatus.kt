package com.emetain.pokemonwebshop.domain

/** Where an order is. Only the verified Stripe webhook moves an order to [PAID]. */
enum class OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED,
    ;

    /** Paid and not cancelled: these count as sold for bestsellers. */
    val countsAsSold: Boolean get() = this in SOLD

    companion object {
        val SOLD = setOf(PAID, PROCESSING, SHIPPED, DELIVERED)

        /** Paid, but not on its way yet: what the shop still has to do. */
        val OPEN = setOf(PAID, PROCESSING)
    }
}

/** What happened to the stock of an order's lines. */
enum class OrderStockState {
    /** Held for the checkout, see `Order.reservedUntil`. */
    RESERVED,

    /** Paid and taken off the stock. */
    SOLD,

    /** The checkout wasn't paid, the reservation went back. */
    RELEASED,

    /** Cancelled or returned after the sale, and put back on the stock. */
    RETURNED,
}

/** For an order with pre-orders and products in stock: wait for everything, or send what's there first. */
enum class ShipmentPreference {
    TOGETHER,
    IN_STOCK_FIRST,
}

/** Who delivers a parcel. The link to follow it is built from `app.checkout.tracking-urls`. */
enum class Carrier {
    POSTNL,
    DHL,
    BPOST,
    DPD,
    OTHER,
}
