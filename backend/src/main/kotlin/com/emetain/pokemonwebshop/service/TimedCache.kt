package com.emetain.pokemonwebshop.service

import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Keeps one value in memory for [ttl]. Callers wait for a refresh, so a slow [load] only runs once at a time. */
class TimedCache<T : Any>(
    private val ttl: Duration,
    private val clock: Clock,
    private val load: () -> T,
) {
    private var value: T? = null
    private var expiresAt: Instant = Instant.MIN

    @Synchronized
    fun get(): T {
        val now = clock.instant()
        val cached = value
        if (cached != null && now.isBefore(expiresAt)) return cached
        return load().also {
            value = it
            expiresAt = now.plus(ttl)
        }
    }
}
