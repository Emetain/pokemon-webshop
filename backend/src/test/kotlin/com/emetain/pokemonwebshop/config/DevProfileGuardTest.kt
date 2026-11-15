package com.emetain.pokemonwebshop.config

import com.emetain.pokemonwebshop.service.testProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains

class DevProfileGuardTest {

    @Test
    fun `dev starts on a local address`() {
        assertDoesNotThrow { DevProfileGuard(testProperties().copy(baseUrl = "http://localhost:5173")) }
    }

    @Test
    fun `dev refuses an https address, which means a real server`() {
        val exception = assertThrows<IllegalStateException> { DevProfileGuard(testProperties().copy(baseUrl = " HTTPS://poketopia.nl")) }

        assertContains(exception.message!!, "SPRING_PROFILES_ACTIVE=prod")
    }
}
