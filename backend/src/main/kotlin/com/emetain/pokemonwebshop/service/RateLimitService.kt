package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.exception.ApiException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Fixed-window limiter kept in memory, so each app instance counts for itself and a restart resets it. */
@Service
class RateLimitService(
    private val properties: AppProperties,
    private val clock: Clock,
) {

    private data class Window(val startedAt: Instant, val attempts: Int)

    private val windows = ConcurrentHashMap<String, Window>()

    fun check(bucket: String, clientIp: String) {
        val now = clock.instant()
        val window = windows.compute("$bucket:$clientIp") { _, current ->
            if (current == null || current.isExpired(now)) Window(now, 1) else current.copy(attempts = current.attempts + 1)
        }!!

        if (window.attempts > properties.rateLimit.maxAttempts) {
            val retryAfter = Duration.between(now, window.startedAt.plus(properties.rateLimit.window))
            throw ApiException.rateLimited(maxOf(retryAfter.toSeconds(), 1))
        }
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun evictExpired() {
        val now = clock.instant()
        windows.values.removeIf { it.isExpired(now) }
    }

    private fun Window.isExpired(now: Instant): Boolean =
        !startedAt.plus(properties.rateLimit.window).isAfter(now)
}
