package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.domain.UserSession
import com.emetain.pokemonwebshop.repository.UserSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class SessionService(
    private val userSessionRepository: UserSessionRepository,
    private val tokenService: TokenService,
    private val properties: AppProperties,
    private val clock: Clock,
) {

    /** Starts a session and returns the plain token for the cookie. */
    @Transactional
    fun create(user: User): String {
        val token = tokenService.generate()
        val now = clock.instant()
        userSessionRepository.save(
            UserSession(
                tokenHash = tokenService.hash(token),
                user = user,
                expiresAt = now.plus(properties.auth.sessionTtl),
                createdAt = now,
            ),
        )
        return token
    }

    @Transactional(readOnly = true)
    fun findUser(token: String): User? =
        userSessionRepository.findActive(tokenService.hash(token), clock.instant())?.user

    @Transactional
    fun delete(token: String) {
        userSessionRepository.deleteByTokenHash(tokenService.hash(token))
    }

    @Transactional
    fun deleteAllForUser(userId: Long) {
        userSessionRepository.deleteAllByUserId(userId)
    }
}
