package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.PremiumSource
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.repository.UserRepository
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Giving Premium from admin (2026-11-21): for friends who help, without paying. */
class AdminPremiumIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var clock: Clock

    private val admin by lazy { loggedInUser(Role.ADMIN) }
    private val today get() = clock.instant().atZone(ZoneId.of("Europe/Amsterdam")).toLocalDate()

    private fun grant(email: String, lastDay: Any? = null, session: Cookie = admin) =
        mockMvc.perform(post("/v1/admin/premium/grants").cookie(session).json(mapOf("email" to email, "lastDay" to lastDay)))

    private fun me(session: Cookie) = mockMvc.perform(get("/v1/premium").cookie(session))

    @Test
    fun `an admin gives a friend Premium for good, and takes it back`() {
        val email = uniqueEmail()
        val friend = loggedInUser(email = email)
        me(friend).andExpect(jsonPath("$.premium").value(false))

        grant(email.uppercase())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.reason").value("FOREVER"))
            .andExpect(jsonPath("$.canRevoke").value(true))
        // Right away, also for a friend who is already logged in.
        me(friend).andExpect(jsonPath("$.premium").value(true)).andExpect(jsonPath("$.reason").value("FOREVER"))
        mockMvc.perform(get("/v1/admin/premium").cookie(admin)).andExpect(jsonPath("$[?(@.email == '$email')].reason").value("FOREVER"))

        val id = userRepository.findByEmail(email)!!.id
        mockMvc.perform(delete("/v1/admin/premium/grants/$id").cookie(admin)).andExpect(status().isNoContent)
        me(friend).andExpect(jsonPath("$.premium").value(false))
    }

    @Test
    fun `Premium until a day lasts through that whole day`() {
        val email = uniqueEmail()
        val friend = loggedInUser(email = email)
        val lastDay = today.plusDays(30)

        grant(email, lastDay.toString())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reason").value("GRANT"))
            .andExpect(jsonPath("$.lastDay").value(lastDay.toString()))

        val user = userRepository.findByEmail(email)!!
        assertEquals(PremiumSource.GRANT, user.premiumSource)
        assertEquals(lastDay.plusDays(1).atStartOfDay(ZoneId.of("Europe/Amsterdam")).toInstant(), user.premiumUntil)
        me(friend).andExpect(jsonPath("$.premium").value(true)).andExpect(jsonPath("$.reason").value("GRANT"))

        // Giving again replaces the earlier gift: from a date to for good.
        grant(email).andExpect(jsonPath("$.reason").value("FOREVER"))
        grant(email, today.minusDays(1).toString()).andExpect(status().isBadRequest)
    }

    @Test
    fun `an unknown address, an admin and a paying customer are refused`() {
        grant(uniqueEmail()).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Er is geen account met dit e-mailadres. Laat de klant eerst een account maken."))

        val otherAdmin = uniqueEmail()
        loggedInUser(Role.ADMIN, otherAdmin)
        grant(otherAdmin).andExpect(status().isBadRequest)

        val paying = uniqueEmail()
        loggedInUser(email = paying)
        val user = userRepository.findByEmail(paying)!!
        user.premiumSource = PremiumSource.SUBSCRIPTION
        user.premiumUntil = Instant.now().plus(20, ChronoUnit.DAYS)
        userRepository.save(user)
        grant(paying).andExpect(status().isBadRequest)
        // A paid subscription can't be taken back here either.
        mockMvc.perform(delete("/v1/admin/premium/grants/${user.id}").cookie(admin)).andExpect(status().isBadRequest)
        mockMvc.perform(get("/v1/admin/premium").cookie(admin))
            .andExpect(jsonPath("$[?(@.email == '$paying')].reason").value("SUBSCRIPTION"))
            .andExpect(jsonPath("$[?(@.email == '$paying')].canRevoke").value(false))
    }

    @Test
    fun `only an admin can give Premium`() {
        val customer = loggedInUser()
        grant(uniqueEmail(), session = customer).andExpect(status().isForbidden)
        mockMvc.perform(get("/v1/admin/premium").cookie(customer)).andExpect(status().isForbidden)
        mockMvc.perform(get("/v1/admin/premium")).andExpect(status().isUnauthorized)
    }

    private fun assertEquals(expected: Any?, actual: Any?) = kotlin.test.assertEquals(expected, actual)
}
