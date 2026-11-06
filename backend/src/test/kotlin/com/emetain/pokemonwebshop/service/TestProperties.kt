package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import java.time.Duration

fun testProperties(maxAttempts: Int = 3, window: Duration = Duration.ofMinutes(1)) = AppProperties(
    baseUrl = "http://localhost:5173",
    mail = AppProperties.Mail(from = "shop@example.com"),
    auth = AppProperties.Auth(
        cookieName = "session",
        cookieSecure = true,
        sessionTtl = Duration.ofDays(30),
        passwordResetTtl = Duration.ofHours(1),
    ),
    rateLimit = AppProperties.RateLimit(maxAttempts = maxAttempts, window = window),
    catalog = testCatalogProperties(),
    collection = AppProperties.Collection(cacheTtl = Duration.ofMinutes(10)),
)

fun testCatalogProperties(baseUrl: String = "http://localhost") = AppProperties.Catalog(
    userAgent = "UithoornTCG/test",
    rateLimitDelay = Duration.ZERO,
    retryDelay = Duration.ZERO,
    tcgdex = AppProperties.CatalogSource(baseUrl, Duration.ZERO),
    scryfall = AppProperties.CatalogSource(baseUrl, Duration.ZERO),
    lorcast = AppProperties.CatalogSource(baseUrl, Duration.ZERO),
    optcg = AppProperties.CatalogSource(baseUrl, Duration.ZERO),
    ecb = AppProperties.CatalogSource(baseUrl, Duration.ZERO),
    pokeapi = AppProperties.CatalogSource(baseUrl, Duration.ZERO),
)
