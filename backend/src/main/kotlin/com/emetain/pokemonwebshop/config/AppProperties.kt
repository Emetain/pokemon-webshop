package com.emetain.pokemonwebshop.config

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
    )
}
