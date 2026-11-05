package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.PasswordResetToken
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.PasswordResetTokenRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

data class IssuedPasswordReset(val user: User, val token: String)

@Service
class PasswordResetService(
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val userService: UserService,
    private val sessionService: SessionService,
    private val tokenService: TokenService,
    private val properties: AppProperties,
    private val clock: Clock,
) {

    /** Replaces any earlier reset of this user. Returns null for an unknown email address. */
    @Transactional
    fun issue(email: String): IssuedPasswordReset? {
        val user = userService.findByEmail(email) ?: return null
        passwordResetTokenRepository.deleteAllByUserId(user.id)

        val token = tokenService.generate()
        val now = clock.instant()
        passwordResetTokenRepository.save(
            PasswordResetToken(
                tokenHash = tokenService.hash(token),
                user = user,
                expiresAt = now.plus(properties.auth.passwordResetTtl),
                createdAt = now,
            ),
        )
        return IssuedPasswordReset(user, token)
    }

    /** Sets the new password and logs the user out everywhere. */
    @Transactional
    fun reset(token: String, newPassword: String) {
        val now = clock.instant()
        val reset = passwordResetTokenRepository.findByTokenHash(tokenService.hash(token))
        if (reset == null || reset.usedAt != null || !reset.expiresAt.isAfter(now)) {
            throw invalidToken()
        }
        if (passwordResetTokenRepository.markUsed(reset.id, now) == 0) {
            throw invalidToken()
        }
        userService.changePassword(reset.user.id, newPassword)
        sessionService.deleteAllForUser(reset.user.id)
    }

    private fun invalidToken() = ApiException(
        HttpStatus.BAD_REQUEST,
        "invalid_token",
        "Deze link is verlopen of al gebruikt. Vraag een nieuwe link aan.",
    )
}
