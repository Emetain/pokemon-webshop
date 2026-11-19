package com.emetain.pokemonwebshop.domain

/** The two ways to pay for Premium (phase 11). The prices are in `app.premium`. */
enum class PremiumPlan {
    MONTHLY,
    YEARLY,
}

/** How a customer got Premium. Admins have it from their role, which is never stored. */
enum class PremiumSource {
    /** A Stripe subscription, kept up to date by its webhooks. */
    SUBSCRIPTION,

    /** Given by the shop, for example to a friend who helps. Can be for good. */
    GRANT,
}
