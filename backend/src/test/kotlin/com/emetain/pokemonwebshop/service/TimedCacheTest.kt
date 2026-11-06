package com.emetain.pokemonwebshop.service

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

class TimedCacheTest {

    private val clock = MutableClock()
    private var loads = 0
    private val cache = TimedCache(Duration.ofMinutes(10), clock) { ++loads }

    @Test
    fun `keeps the value until it expires`() {
        assertEquals(1, cache.get())

        clock.advance(Duration.ofMinutes(9))

        assertEquals(1, cache.get())
        assertEquals(1, loads)
    }

    @Test
    fun `loads again after it expires`() {
        cache.get()

        clock.advance(Duration.ofMinutes(10))

        assertEquals(2, cache.get())
    }

    @Test
    fun `a time to live of zero never keeps anything`() {
        val uncached = TimedCache(Duration.ZERO, clock) { ++loads }

        uncached.get()

        assertEquals(2, uncached.get())
    }
}
