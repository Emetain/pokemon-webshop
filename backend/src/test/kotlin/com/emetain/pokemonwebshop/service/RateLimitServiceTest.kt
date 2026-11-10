package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.exception.ApiException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.time.Duration
import kotlin.test.assertEquals

class RateLimitServiceTest {

    private val clock = MutableClock()
    private val service = RateLimitService(testProperties(maxAttempts = 3), clock)

    @Test
    fun `allows attempts up to the limit and blocks the next one`() {
        repeat(3) { service.check("login", "1.1.1.1") }

        val exception = assertThrows<ApiException> { service.check("login", "1.1.1.1") }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.status)
        assertEquals("rate_limited", exception.code)
    }

    @Test
    fun `tells the client how long to wait`() {
        repeat(3) { service.check("login", "1.1.1.1") }
        clock.advance(Duration.ofSeconds(20))

        val exception = assertThrows<ApiException> { service.check("login", "1.1.1.1") }

        assertEquals(40, exception.retryAfterSeconds)
    }

    @Test
    fun `counts each ip address separately`() {
        repeat(3) { service.check("login", "1.1.1.1") }

        assertDoesNotThrow { service.check("login", "2.2.2.2") }
    }

    @Test
    fun `counts each bucket separately`() {
        repeat(3) { service.check("login", "1.1.1.1") }

        assertDoesNotThrow { service.check("register", "1.1.1.1") }
    }

    @Test
    fun `starts counting again after the window`() {
        repeat(3) { service.check("login", "1.1.1.1") }
        clock.advance(Duration.ofMinutes(1))

        assertDoesNotThrow { service.check("login", "1.1.1.1") }
    }

    @Test
    fun `evicting expired windows keeps active ones`() {
        repeat(3) { service.check("login", "1.1.1.1") }
        clock.advance(Duration.ofSeconds(30))
        service.check("login", "2.2.2.2")
        clock.advance(Duration.ofSeconds(30))

        service.evictExpired()

        assertDoesNotThrow { service.check("login", "1.1.1.1") }
        repeat(2) { service.check("login", "2.2.2.2") }
        assertThrows<ApiException> { service.check("login", "2.2.2.2") }
    }

    @Test
    fun `a bucket with its own limit uses it, and accounts are counted apart from addresses`() {
        val properties = testProperties(maxAttempts = 3).let {
            it.copy(rateLimit = it.rateLimit.copy(buckets = mapOf("drop-ping" to com.emetain.pokemonwebshop.config.AppProperties.Bucket(maxAttempts = 5))))
        }
        val limiter = RateLimitService(properties, clock)

        repeat(5) { limiter.check("drop-ping", "1.1.1.1") }
        assertThrows<ApiException> { limiter.check("drop-ping", "1.1.1.1") }

        repeat(3) { limiter.checkAccount("drop-cart-account", 7) }
        assertThrows<ApiException> { limiter.checkAccount("drop-cart-account", 7) }
        assertDoesNotThrow { limiter.checkAccount("drop-cart-account", 8) }
    }
}
