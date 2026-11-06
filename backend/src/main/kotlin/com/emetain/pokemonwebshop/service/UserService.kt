package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.SUPPORTED_COUNTRIES
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock,
) {

    // Compared against when the email is unknown, so a login takes equally long either way.
    private val dummyPasswordHash by lazy { passwordEncoder.encode(UUID.randomUUID().toString()) }

    @Transactional
    fun register(email: String, password: String, displayName: String, role: Role = Role.CUSTOMER): User {
        val normalizedEmail = normalizeEmail(email)
        requireUsablePassword(password)
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw ApiException(HttpStatus.CONFLICT, "email_taken", "Er bestaat al een account met dit e-mailadres.")
        }
        return userRepository.save(
            User(
                email = normalizedEmail,
                passwordHash = passwordEncoder.encode(password),
                displayName = displayName.trim(),
                role = role,
                createdAt = clock.instant(),
            ),
        )
    }

    @Transactional(readOnly = true)
    fun authenticate(email: String, password: String): User {
        val user = userRepository.findByEmail(normalizeEmail(email))
        val passwordMatches = passwordEncoder.matches(password, user?.passwordHash ?: dummyPasswordHash)
        if (user == null || !passwordMatches) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "E-mailadres of wachtwoord klopt niet.")
        }
        return user
    }

    @Transactional(readOnly = true)
    fun findByEmail(email: String): User? = userRepository.findByEmail(normalizeEmail(email))

    @Transactional
    fun updateProfile(userId: Long, displayName: String?, country: String?, currency: Currency? = null): User {
        val user = userRepository.findById(userId).orElseThrow { ApiException.notAuthenticated() }
        displayName?.let { user.displayName = it.trim() }
        country?.let {
            requireSupportedCountry(it)
            user.country = it
        }
        currency?.let { user.currency = it }
        return user
    }

    @Transactional
    fun changePassword(userId: Long, password: String) {
        requireUsablePassword(password)
        val user = userRepository.findById(userId).orElseThrow { ApiException.notAuthenticated() }
        user.passwordHash = passwordEncoder.encode(password)
    }

    fun requireSupportedCountry(country: String) {
        if (country !in SUPPORTED_COUNTRIES) {
            throw ApiException.validation("We leveren nog niet in dit land.")
        }
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    private fun requireUsablePassword(password: String) {
        // BCrypt ignores everything after 72 bytes, so a longer password would be weaker than it looks.
        if (password.toByteArray().size > MAX_PASSWORD_BYTES) {
            throw ApiException.validation("Dit wachtwoord is te lang. Kies een korter wachtwoord.")
        }
    }

    private companion object {
        const val MAX_PASSWORD_BYTES = 72
    }
}
