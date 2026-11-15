package com.emetain.pokemonwebshop.service.payment

import com.emetain.pokemonwebshop.config.ShopAccess

/**
 * Which Stripe key fits how open the shop is (`SHOP_ACCESS`). A live key in the closed preview would take real money
 * while Stripe's webhooks can't get past the preview login, so paid orders would stay unpaid. A test key in the open
 * shop would let customers "pay" with Stripe's test cards and get their order. Either way the shop keeps online
 * payment off, logs why, and the admin dashboard says so.
 */
object StripeKeyRules {

    enum class Problem(
        /** For the admin dashboard, which has the Dutch text. */
        val code: String,
        val logMessage: String,
    ) {
        LIVE_KEY_IN_PREVIEW(
            "live_key_in_preview",
            "STRIPE_SECRET_KEY is a live key, but SHOP_ACCESS is preview: Stripe's webhooks can't get past the preview " +
                "login, so online payment stays off. Use a test key in the preview, or open the shop.",
        ),
        TEST_KEY_IN_OPEN_SHOP(
            "test_key_in_open_shop",
            "STRIPE_SECRET_KEY is not a live key, but SHOP_ACCESS is open: customers could pay with test cards, so " +
                "online payment stays off. Put the live key (sk_live_...) in the server's .env.",
        ),
    }

    /** Null when the key may be used, or when there is none (online payment is simply off then). */
    fun problem(secretKey: String, access: ShopAccess): Problem? {
        if (secretKey.isBlank()) return null
        val live = LIVE_PREFIXES.any { secretKey.trim().startsWith(it) }
        return when {
            access == ShopAccess.PREVIEW && live -> Problem.LIVE_KEY_IN_PREVIEW
            access == ShopAccess.OPEN && !live -> Problem.TEST_KEY_IN_OPEN_SHOP
            else -> null
        }
    }

    /** Secret and restricted live keys. Test keys start with `sk_test_` or `rk_test_`. */
    private val LIVE_PREFIXES = listOf("sk_live_", "rk_live_")
}
