package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.domain.UserSession
import com.emetain.pokemonwebshop.repository.UserSessionRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SessionServiceTest {

    private val clock = MutableClock()
    private val tokenService = TokenService()
    private val repository = mock<UserSessionRepository> {
        on { save(any<UserSession>()) } doAnswer { it.getArgument(0) }
    }
    private val service = SessionService(repository, tokenService, testProperties(), clock)
    private val user = User("ash@example.com", "hash", "Ash", createdAt = Instant.EPOCH, id = 7)

    @Test
    fun `stores only the hash of the token`() {
        val token = service.create(user)

        val saved = argumentCaptor<UserSession>().apply { verify(repository).save(capture()) }.firstValue
        assertNotEquals(token, saved.tokenHash)
        assertEquals(tokenService.hash(token), saved.tokenHash)
    }

    @Test
    fun `sessions expire after the configured time`() {
        service.create(user)

        val saved = argumentCaptor<UserSession>().apply { verify(repository).save(capture()) }.firstValue
        assertEquals(clock.instant().plus(Duration.ofDays(30)), saved.expiresAt)
    }

    @Test
    fun `tokens are unique and url safe`() {
        val tokens = List(100) { tokenService.generate() }

        assertEquals(100, tokens.toSet().size)
        tokens.forEach { assert(it.matches(Regex("[A-Za-z0-9_-]{43}"))) }
    }
}
