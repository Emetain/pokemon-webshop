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

/**
 * Fixed-window limiter kept in memory, so each app instance counts for itself and a restart resets it. A bucket uses
 * the default limit unless `app.rate-limit.buckets` gives it its own.
 */
@Service
class RateLimitService(
    private val properties: AppProperties,
    private val clock: Clock,
) {

    private data class Window(val startedAt: Instant, val attempts: Int, val length: Duration)

    private val windows = ConcurrentHashMap<String, Window>()

    /** Per IP address, for endpoints marked with `@RateLimited`. */
    fun check(bucket: String, clientIp: String) = count(bucket, clientIp)

    /** Per account, for endpoints where many people can share one IP address, such as a phone network. */
    fun checkAccount(bucket: String, userId: Long) = count(bucket, "user-$userId")

    private fun count(bucket: String, key: String) {
        val now = clock.instant()
        val limits = properties.rateLimit
        val length = limits.window(bucket)
        val window = windows.compute("$bucket:$key") { _, current ->
            if (current == null || current.isExpired(now)) Window(now, 1, length) else current.copy(attempts = current.attempts + 1)
        }!!

        if (window.attempts > limits.maxAttempts(bucket)) {
            val retryAfter = Duration.between(now, window.startedAt.plus(window.length))
            throw ApiException.rateLimited(maxOf(retryAfter.toSeconds(), 1))
        }
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun evictExpired() {
        val now = clock.instant()
        windows.values.removeIf { it.isExpired(now) }
    }

    private fun Window.isExpired(now: Instant): Boolean = !startedAt.plus(length).isAfter(now)
}
