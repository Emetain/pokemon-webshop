package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.PasswordResetToken
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.PasswordResetTokenRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PasswordResetServiceTest {

    private val clock = MutableClock()
    private val tokenService = TokenService()
    private val user = User("ash@example.com", "hash", "Ash", createdAt = Instant.EPOCH, id = 7)
    private val repository = mock<PasswordResetTokenRepository> {
        on { save(any<PasswordResetToken>()) } doAnswer { it.getArgument(0) }
        on { markUsed(any(), any()) } doReturn 1
    }
    private val userService = mock<UserService>()
    private val sessionService = mock<SessionService>()
    private val service = PasswordResetService(repository, userService, sessionService, tokenService, testProperties(), clock)

    private fun storedToken(token: String, expiresIn: Duration = Duration.ofMinutes(30), usedAt: Instant? = null) {
        val stored = PasswordResetToken(
            tokenHash = tokenService.hash(token),
            user = user,
            expiresAt = clock.instant().plus(expiresIn),
            createdAt = clock.instant(),
            usedAt = usedAt,
            id = 1,
        )
        whenever(repository.findByTokenHash(tokenService.hash(token))) doReturn stored
    }

    @Test
    fun `issue creates a token that expires after one hour and stores only its hash`() {
        whenever(userService.findByEmail("ash@example.com")) doReturn user

        val issued = assertNotNull(service.issue("ash@example.com"))

        val saved = argumentCaptor<PasswordResetToken>().apply { verify(repository).save(capture()) }.firstValue
        assertEquals(tokenService.hash(issued.token), saved.tokenHash)
        assertEquals(clock.instant().plus(Duration.ofHours(1)), saved.expiresAt)
    }

    @Test
    fun `issue replaces earlier tokens of the user`() {
        whenever(userService.findByEmail("ash@example.com")) doReturn user

        service.issue("ash@example.com")

        verify(repository).deleteAllByUserId(7)
    }

    @Test
    fun `issue returns nothing for an unknown email`() {
        assertNull(service.issue("misty@example.com"))

        verify(repository, never()).save(any<PasswordResetToken>())
    }

    @Test
    fun `reset changes the password and ends all sessions`() {
        storedToken("valid")

        service.reset("valid", "nieuwwachtwoord")

        verify(repository).markUsed(1, clock.instant())
        verify(userService).changePassword(7, "nieuwwachtwoord")
        verify(sessionService).deleteAllForUser(7)
    }

    @Test
    fun `reset refuses an unknown token`() {
        assertInvalidToken("unknown")
    }

    @Test
    fun `reset refuses a token at the moment it expires`() {
        storedToken("expired", expiresIn = Duration.ZERO)

        assertInvalidToken("expired")
    }

    @Test
    fun `reset refuses a used token`() {
        storedToken("used", usedAt = clock.instant())

        assertInvalidToken("used")
    }

    @Test
    fun `reset refuses a token that a parallel request claimed first`() {
        storedToken("raced")
        whenever(repository.markUsed(any(), any())) doReturn 0

        assertInvalidToken("raced")
    }

    private fun assertInvalidToken(token: String) {
        val exception = assertThrows<ApiException> { service.reset(token, "nieuwwachtwoord") }

        assertEquals("invalid_token", exception.code)
        verify(userService, never()).changePassword(any(), any())
    }
}
