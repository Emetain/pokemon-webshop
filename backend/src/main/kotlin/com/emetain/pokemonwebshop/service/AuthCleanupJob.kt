package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.repository.PasswordResetTokenRepository
import com.emetain.pokemonwebshop.repository.UserSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** Removes sessions and reset tokens that can no longer be used. */
@Service
class AuthCleanupJob(
    private val userSessionRepository: UserSessionRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.auth.cleanup-cron}", zone = "UTC")
    @Transactional
    fun run() {
        val now = clock.instant()
        val sessions = userSessionRepository.deleteExpired(now)
        val tokens = passwordResetTokenRepository.deleteExpiredOrUsed(now)
        log.info("Removed {} expired sessions and {} stale password reset tokens", sessions, tokens)
    }
}
