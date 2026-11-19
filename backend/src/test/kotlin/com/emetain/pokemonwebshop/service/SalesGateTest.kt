package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.ShopAccess
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SalesGateTest {

    private fun gate(access: ShopAccess) = SalesGate(testProperties().copy(shopAccess = access))

    private fun user(role: Role) =
        User(email = "${role.name.lowercase()}@example.test", passwordHash = "x", displayName = "Test", role = role, createdAt = Instant.EPOCH)

    @Test
    fun `online without the login only admins can order`() {
        val gate = gate(ShopAccess.UNLISTED)

        assertTrue(gate.canOrder(user(Role.ADMIN)))
        assertFalse(gate.canOrder(user(Role.CUSTOMER)))
        assertFalse(gate.canOrder(null))
        val refused = assertThrows<ApiException> { gate.requireCanOrder(null) }
        assertEquals("shop_not_open", refused.code)
    }

    @Test
    fun `in the preview and the open shop everyone can order`() {
        for (access in listOf(ShopAccess.PREVIEW, ShopAccess.OPEN)) {
            assertTrue(gate(access).canOrder(null))
            assertTrue(gate(access).canOrder(user(Role.CUSTOMER)))
            gate(access).requireCanOrder(null)
        }
    }
}
