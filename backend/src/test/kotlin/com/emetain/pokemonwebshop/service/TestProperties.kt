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
)
