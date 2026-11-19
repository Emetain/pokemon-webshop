package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.PremiumSource
import com.emetain.pokemonwebshop.domain.PremiumSubscription
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.User
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PremiumAccessTest {

    private val now = Instant.parse("2026-11-19T12:00:00Z")
    private val month = Duration.ofDays(30)

    private fun user(role: Role = Role.CUSTOMER) =
        User(email = "a@example.com", passwordHash = "x", displayName = "A", role = role, createdAt = now.minus(month))

    private fun subscription(user: User, status: String, end: Instant?) = PremiumSubscription(
        user = user,
        stripeSubscriptionId = "sub_$status",
        status = status,
        currentPeriodEnd = end,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `admins always have Premium, from their role`() {
        val admin = user(Role.ADMIN)
        assertEquals(PremiumReason.ADMIN, PremiumAccess.reason(admin, now))
        assertNull(admin.premiumUntil)
    }

    @Test
    fun `a paid-up subscription gives Premium until the end of its period`() {
        val user = user()
        PremiumAccess.recompute(user, listOf(subscription(user, "active", now.plus(month))), now)

        assertEquals(now.plus(month), user.premiumUntil)
        assertEquals(now, user.premiumSince)
        assertEquals(PremiumSource.SUBSCRIPTION, user.premiumSource)
        assertTrue(PremiumAccess.has(user, now.plus(month).minusSeconds(1)))
        assertFalse(PremiumAccess.has(user, now.plus(month)))
    }

    @Test
    fun `a failed renewal ends Premium now`() {
        val user = user()
        PremiumAccess.recompute(user, listOf(subscription(user, "active", now.plus(month))), now)

        PremiumAccess.recompute(user, listOf(subscription(user, "past_due", now.plus(month))), now.plusSeconds(60))

        assertFalse(PremiumAccess.has(user, now.plusSeconds(61)))
    }

    @Test
    fun `a renewal keeps the start date`() {
        val user = user()
        PremiumAccess.recompute(user, listOf(subscription(user, "active", now.plus(month))), now)
        val later = now.plus(Duration.ofDays(29))

        PremiumAccess.recompute(user, listOf(subscription(user, "active", now.plus(month).plus(month))), later)

        assertEquals(now, user.premiumSince)
        assertEquals(now.plus(month).plus(month), user.premiumUntil)
    }

    @Test
    fun `an ended subscription leaves a second one that still runs`() {
        val user = user()
        val subscriptions = listOf(subscription(user, "canceled", now), subscription(user, "active", now.plus(month)))

        PremiumAccess.recompute(user, subscriptions, now)

        assertEquals(now.plus(month), user.premiumUntil)
    }

    @Test
    fun `a grant for good is never taken away by a subscription`() {
        val user = user().apply {
            premiumForever = true
            premiumSource = PremiumSource.GRANT
        }

        PremiumAccess.recompute(user, listOf(subscription(user, "canceled", now)), now)

        assertEquals(PremiumReason.FOREVER, PremiumAccess.reason(user, now))
    }
}
