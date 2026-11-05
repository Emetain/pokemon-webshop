package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UserServiceTest {

    private val encoder = BCryptPasswordEncoder(4)
    private val repository = mock<UserRepository> {
        on { save(any<User>()) } doAnswer { it.getArgument(0) }
    }
    private val service = UserService(repository, encoder, MutableClock())

    private fun user(password: String = "geheim123") = User(
        email = "ash@example.com",
        passwordHash = encoder.encode(password),
        displayName = "Ash",
        createdAt = Instant.EPOCH,
        id = 7,
    )

    @Test
    fun `register normalizes the email and hashes the password`() {
        val user = service.register("  Ash@Example.COM ", "geheim123", " Ash ")

        assertEquals("ash@example.com", user.email)
        assertEquals("Ash", user.displayName)
        assertEquals(Role.CUSTOMER, user.role)
        assertEquals("NL", user.country)
        assertNotEquals("geheim123", user.passwordHash)
        assertTrue(encoder.matches("geheim123", user.passwordHash))
    }

    @Test
    fun `register refuses an email that is already taken`() {
        whenever(repository.existsByEmail("ash@example.com")) doReturn true

        val exception = assertThrows<ApiException> { service.register("ASH@example.com", "geheim123", "Ash") }

        assertEquals(HttpStatus.CONFLICT, exception.status)
        assertEquals("email_taken", exception.code)
        verify(repository, never()).save(any<User>())
    }

    @Test
    fun `register refuses a password longer than bcrypt can use`() {
        val exception = assertThrows<ApiException> { service.register("ash@example.com", "é".repeat(37), "Ash") }

        assertEquals("validation_failed", exception.code)
    }

    @Test
    fun `authenticate returns the user for the right password`() {
        val user = user()
        whenever(repository.findByEmail("ash@example.com")) doReturn user

        assertEquals(user, service.authenticate(" ASH@example.com", "geheim123"))
    }

    @Test
    fun `authenticate gives the same error for a wrong password and an unknown email`() {
        whenever(repository.findByEmail("ash@example.com")) doReturn user()

        val wrongPassword = assertThrows<ApiException> { service.authenticate("ash@example.com", "fout") }
        val unknownEmail = assertThrows<ApiException> { service.authenticate("misty@example.com", "fout") }

        assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.status)
        assertEquals(wrongPassword.code, unknownEmail.code)
        assertEquals(wrongPassword.message, unknownEmail.message)
    }

    @Test
    fun `updateProfile only changes the given fields`() {
        val user = user()
        whenever(repository.findById(7)) doReturn Optional.of(user)

        service.updateProfile(7, displayName = null, country = "BE")

        assertEquals("Ash", user.displayName)
        assertEquals("BE", user.country)
    }

    @Test
    fun `updateProfile refuses a country the shop does not serve`() {
        val user = user()
        whenever(repository.findById(7)) doReturn Optional.of(user)

        assertThrows<ApiException> { service.updateProfile(7, displayName = null, country = "US") }

        assertEquals("NL", user.country)
    }
}
