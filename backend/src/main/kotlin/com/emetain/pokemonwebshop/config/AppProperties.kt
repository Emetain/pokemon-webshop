package com.emetain.pokemonwebshop.config

import com.emetain.pokemonwebshop.domain.Carrier
import com.emetain.pokemonwebshop.domain.Game
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.LocalDate

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val baseUrl: String,
    val mail: Mail,
    val auth: Auth,
    val rateLimit: RateLimit,
    val catalog: Catalog,
    val collection: Collection,
    val media: Media,
    val shop: Shop,
    val checkout: Checkout = Checkout(),
    val payments: Payments = Payments(),
    val drops: Drops = Drops(),
) {
    data class Mail(
        val from: String,
    )

    data class Auth(
        val cookieName: String,
        val cookieSecure: Boolean,
        val sessionTtl: Duration,
        val passwordResetTtl: Duration,
    )

    data class RateLimit(
        val maxAttempts: Int,
        val window: Duration,
        /** Buckets that need another limit than the default, such as the waiting room that asks every few seconds. */
        val buckets: Map<String, Bucket> = emptyMap(),
    ) {
        fun maxAttempts(bucket: String): Int = buckets[bucket]?.maxAttempts ?: maxAttempts

        fun window(bucket: String): Duration = buckets[bucket]?.window ?: window
    }

    data class Bucket(
        val maxAttempts: Int,
        val window: Duration? = null,
    )

    data class Catalog(
        val userAgent: String,
        val rateLimitDelay: Duration,
        val retryDelay: Duration,
        val tcgdex: CatalogSource,
        val scryfall: CatalogSource,
        val lorcast: CatalogSource,
        val optcg: CatalogSource,
        val ecb: CatalogSource,
        val pokeapi: CatalogSource,
        val importPokedexOnStart: Boolean,
    )

    data class CatalogSource(
        val baseUrl: String,
        val requestDelay: Duration,
    )

    data class Collection(
        val cacheTtl: Duration,
    )

    data class Media(
        val directory: String,
    )

    data class Shop(
        /** Cardmarket's public product list, for the product import. */
        val cardmarket: CatalogSource,
        /**
         * Per game, the release date of the oldest set that is still in print. The shop hides older sets. A game
         * without a date keeps every set.
         */
        val inPrintFrom: Map<Game, LocalDate> = emptyMap(),
        /** Per game, the codes of the most popular sets, most popular first. "Populair" sorts on this. */
        val popularSets: Map<Game, List<String>> = emptyMap(),
        /** "Populair" and bestsellers count the units sold in this period, before the popular sets. */
        val bestsellerPeriod: Duration = Duration.ofDays(90),
    )

    data class Checkout(
        /** How long a checkout holds the stock while the customer pays. */
        val reservationTtl: Duration = Duration.ofMinutes(15),
        /** A guest cart that hasn't changed for this long is deleted. */
        val guestCartTtl: Duration = Duration.ofDays(60),
        val cartCookieName: String = "cart",
        /** Per carrier, the link to follow a parcel, with `{code}`, `{postcode}` and `{country}`. */
        val trackingUrls: Map<Carrier, String> = emptyMap(),
        /** Shipping rates created at startup for supported countries that have none yet. The admin changes them. */
        val defaultRates: Map<String, DefaultRate> = emptyMap(),
    )

    data class DefaultRate(
        val priceCents: Long,
        val freeFromCents: Long? = null,
        val deliveryTime: String,
    )

    data class Payments(
        val stripe: Stripe = Stripe(),
    )

    data class Stripe(
        /** A test key (`sk_test_...`) locally. Empty switches online payment off. */
        val secretKey: String = "",
        /** The `whsec_...` secret that proves a webhook comes from Stripe. */
        val webhookSecret: String = "",
    ) {
        val configured: Boolean get() = secretKey.isNotBlank()
    }

    data class Drops(
        /** How often the drop scheduler looks at the clock, in Dutch time. "-" switches it off. */
        val tickCron: String = "* * * * * *",
        /** The admission speed of a new drop. The admin changes it per drop, also during the drop. */
        val admitPerMinute: Int = 60,
        /** The waiting room lets people through in batches this far apart. */
        val admitInterval: Duration = Duration.ofSeconds(10),
        /** How long someone who got through may shop and start a checkout. */
        val accessTtl: Duration = Duration.ofMinutes(10),
        /** How long a drop checkout holds the stock. Shorter than the shop, so unpaid stock goes back to the line sooner. */
        val reservationTtl: Duration = Duration.ofMinutes(10),
        /** Someone who hasn't asked for their place for this long isn't counted when the next batch is let through. */
        val activeWindow: Duration = Duration.ofSeconds(75),
        /** How long before the start the waiting room opens. */
        val waitingRoomOpens: Duration = Duration.ofMinutes(30),
        /** How long before the start the "herinner mij" mails go out. */
        val reminderBefore: Duration = Duration.ofMinutes(15),
        val visitorCookieName: String = "drop_visitor",
        val accessCookieName: String = "drop_access",
    )
}
