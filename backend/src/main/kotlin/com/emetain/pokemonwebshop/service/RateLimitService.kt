package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.exception.ApiException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
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

    /**
     * Per email address, whether or not it has an account, for endpoints where an attacker can use many IP addresses
     * against one customer, such as reset mails.
     */
    fun checkEmail(bucket: String, email: String) = count(bucket, emailKey(email))

    /**
     * Refuses when [email] has used up [bucket], without counting this attempt. Count only what should count, such
     * as a failed login, with [recordEmail]. A correct password is refused too while the limit holds, or the limit
     * wouldn't stop guessing.
     */
    fun requireEmailBelowLimit(bucket: String, email: String) {
        val now = clock.instant()
        val window = windows["$bucket:${emailKey(email)}"]?.takeUnless { it.isExpired(now) } ?: return
        if (window.attempts >= properties.rateLimit.maxAttempts(bucket)) throw limited(window, now)
    }

    fun recordEmail(bucket: String, email: String) {
        increment(bucket, emailKey(email))
    }

    private fun emailKey(email: String) = "email-" + email.trim().lowercase(Locale.ROOT)

    private fun count(bucket: String, key: String) {
        val now = clock.instant()
        val window = increment(bucket, key)
        if (window.attempts > properties.rateLimit.maxAttempts(bucket)) throw limited(window, now)
    }

    private fun increment(bucket: String, key: String): Window {
        val now = clock.instant()
        val length = properties.rateLimit.window(bucket)
        return windows.compute("$bucket:$key") { _, current ->
            if (current == null || current.isExpired(now)) Window(now, 1, length) else current.copy(attempts = current.attempts + 1)
        }!!
    }

    private fun limited(window: Window, now: Instant): ApiException {
        val retryAfter = Duration.between(now, window.startedAt.plus(window.length))
        return ApiException.rateLimited(maxOf(retryAfter.toSeconds(), 1))
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun evictExpired() {
        val now = clock.instant()
        windows.values.removeIf { it.isExpired(now) }
    }

    private fun Window.isExpired(now: Instant): Boolean = !startedAt.plus(length).isAfter(now)
}
