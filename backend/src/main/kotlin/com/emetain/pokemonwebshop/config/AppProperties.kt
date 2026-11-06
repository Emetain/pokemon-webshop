package com.emetain.pokemonwebshop.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val baseUrl: String,
    val mail: Mail,
    val auth: Auth,
    val rateLimit: RateLimit,
    val catalog: Catalog,
    val collection: Collection,
    val media: Media,
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
}
