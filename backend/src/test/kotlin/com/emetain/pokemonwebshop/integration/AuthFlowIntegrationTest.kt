package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.PasswordResetToken
import com.emetain.pokemonwebshop.domain.UserSession
import com.emetain.pokemonwebshop.repository.PasswordResetTokenRepository
import com.emetain.pokemonwebshop.repository.UserRepository
import com.emetain.pokemonwebshop.repository.UserSessionRepository
import com.emetain.pokemonwebshop.service.AuthCleanupJob
import com.emetain.pokemonwebshop.service.TokenService
import jakarta.mail.Message
import jakarta.servlet.http.Cookie
import jakarta.mail.Multipart
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthFlowIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordResetTokenRepository: PasswordResetTokenRepository

    @Autowired
    private lateinit var userSessionRepository: UserSessionRepository

    @Autowired
    private lateinit var authCleanupJob: AuthCleanupJob

    @Autowired
    private lateinit var tokenService: TokenService

    private fun register(email: String) = mockMvc.perform(
        post("/v1/auth/register").with(fromRandomIp())
            .json(mapOf("email" to email, "password" to password, "displayName" to "Ash")),
    )

    private fun forgotPassword(email: String) = mockMvc.perform(
        post("/v1/auth/forgot-password").with(fromRandomIp()).json(mapOf("email" to email)),
    )

    private fun resetPassword(token: String, newPassword: String) = mockMvc.perform(
        post("/v1/auth/reset-password").with(fromRandomIp())
            .json(mapOf("token" to token, "password" to newPassword)),
    )

    private fun tokenFromMailTo(email: String): String {
        val mail = await().atMost(Duration.ofSeconds(5)).until(
            { mailSender.sent.lastOrNull { it.getRecipients(Message.RecipientType.TO).first().toString() == email } },
            { it != null },
        )!!
        val text = assertNotNull(plainText(mail))
        return assertNotNull(Regex("token=([A-Za-z0-9_-]+)").find(text)).groupValues[1]
    }

    @Test
    fun `register, who am I, log out and log in again`() {
        val email = uniqueEmail()

        val registered = register(email)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.country").value("NL"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
        val session = registered.sessionCookie()

        mockMvc.perform(get("/v1/auth/me").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(email))

        val logout = mockMvc.perform(post("/v1/auth/logout").cookie(session))
            .andExpect(status().isNoContent)
        assertEquals(0, logout.sessionCookie().maxAge)

        mockMvc.perform(get("/v1/auth/me").cookie(session))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("not_authenticated"))

        val loggedIn = login(email).andExpect(status().isOk).sessionCookie()
        mockMvc.perform(get("/v1/auth/me").cookie(loggedIn)).andExpect(status().isOk)
    }

    @Test
    fun `the session cookie is http only, secure and same site lax`() {
        val header = assertNotNull(register(uniqueEmail()).andReturn().response.getHeader(HttpHeaders.SET_COOKIE))

        assertTrue("HttpOnly" in header)
        assertTrue("Secure" in header)
        assertTrue("SameSite=Lax" in header)
    }

    @Test
    fun `an email can only register once, whatever its casing`() {
        val email = uniqueEmail()
        register(email).andExpect(status().isCreated)

        register(email.uppercase())
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("email_taken"))
    }

    @Test
    fun `a wrong password is refused with a dutch message`() {
        val email = uniqueEmail()
        register(email)

        login(email, "ditklopthelemaalniet")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("invalid_credentials"))
            .andExpect(jsonPath("$.message").value("E-mailadres of wachtwoord klopt niet."))
    }

    @Test
    fun `password reset by mail changes the password and is single use`() {
        val email = uniqueEmail()
        val oldSession = register(email).sessionCookie()

        forgotPassword(email).andExpect(status().isNoContent)
        val token = tokenFromMailTo(email)

        resetPassword(token, "eennieuwwachtwoord").andExpect(status().isNoContent)

        login(email).andExpect(status().isUnauthorized)
        login(email, "eennieuwwachtwoord").andExpect(status().isOk)
        mockMvc.perform(get("/v1/auth/me").cookie(oldSession)).andExpect(status().isUnauthorized)

        resetPassword(token, "nogeenwachtwoord")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_token"))
        login(email, "eennieuwwachtwoord").andExpect(status().isOk)
    }

    @Test
    fun `the reset mail has an html and a text version`() {
        val email = uniqueEmail()
        register(email)

        forgotPassword(email)
        tokenFromMailTo(email)

        val mail = mailSender.sent.last { it.getRecipients(Message.RecipientType.TO).first().toString() == email }
        val alternative = (mail.content as Multipart).getBodyPart(0).content as Multipart
        val parts = (alternative.getBodyPart(0).content as Multipart)
        assertTrue(parts.getBodyPart(0).isMimeType("text/plain"))
        assertTrue(parts.getBodyPart(1).isMimeType("text/html"))
    }

    @Test
    fun `an expired reset token does not work`() {
        val email = uniqueEmail()
        register(email)
        val user = assertNotNull(userRepository.findByEmail(email))
        passwordResetTokenRepository.save(
            PasswordResetToken(
                tokenHash = tokenService.hash("expired-token"),
                user = user,
                expiresAt = Instant.now().minusSeconds(1),
                createdAt = Instant.now().minus(Duration.ofHours(1)),
            ),
        )

        resetPassword("expired-token", "eennieuwwachtwoord")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_token"))
    }

    @Test
    fun `asking a new reset link cancels the previous one`() {
        val email = uniqueEmail()
        register(email)

        forgotPassword(email)
        val firstToken = tokenFromMailTo(email)
        val mailCount = mailSender.sent.size
        forgotPassword(email)
        await().atMost(Duration.ofSeconds(5)).until { mailSender.sent.size > mailCount }

        resetPassword(firstToken, "eennieuwwachtwoord").andExpect(status().isBadRequest)
    }

    @Test
    fun `an expired session counts as logged out`() {
        val email = uniqueEmail()
        register(email)
        val user = assertNotNull(userRepository.findByEmail(email))
        userSessionRepository.save(
            UserSession(
                tokenHash = tokenService.hash("expired-session"),
                user = user,
                expiresAt = Instant.now().minusSeconds(1),
                createdAt = Instant.now().minus(Duration.ofDays(30)),
            ),
        )

        mockMvc.perform(get("/v1/auth/me").cookie(Cookie("session", "expired-session")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `the cleanup job removes what can no longer be used and keeps the rest`() {
        val email = uniqueEmail()
        val activeSession = register(email).sessionCookie()
        val user = assertNotNull(userRepository.findByEmail(email))
        val now = Instant.now()
        val expiredSession = userSessionRepository.save(
            UserSession(tokenService.hash("old-session"), user, now.minusSeconds(1), now.minus(Duration.ofDays(30))),
        )
        val expiredToken = passwordResetTokenRepository.save(
            PasswordResetToken(tokenService.hash("old-token"), user, now.minusSeconds(1), now.minus(Duration.ofHours(1))),
        )
        val usedToken = passwordResetTokenRepository.save(
            PasswordResetToken(tokenService.hash("used-token"), user, now.plusSeconds(600), now, usedAt = now),
        )
        val openToken = passwordResetTokenRepository.save(
            PasswordResetToken(tokenService.hash("open-token"), user, now.plusSeconds(600), now),
        )

        authCleanupJob.run()

        assertFalse(userSessionRepository.existsById(expiredSession.id))
        assertFalse(passwordResetTokenRepository.existsById(expiredToken.id))
        assertFalse(passwordResetTokenRepository.existsById(usedToken.id))
        assertTrue(passwordResetTokenRepository.existsById(openToken.id))
        mockMvc.perform(get("/v1/auth/me").cookie(activeSession)).andExpect(status().isOk)
    }

    @Test
    fun `forgot password does not reveal whether an account exists`() {
        val email = uniqueEmail()

        forgotPassword(email).andExpect(status().isNoContent)

        assertFalse(mailSender.sent.any { it.getRecipients(Message.RecipientType.TO).first().toString() == email })
    }
}
