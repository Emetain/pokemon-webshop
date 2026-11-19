package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.PremiumPlan
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.service.payment.RefundedCharge
import com.emetain.pokemonwebshop.service.payment.SubscriptionState
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Phase 11: Premium as a Stripe subscription, switched on, renewed and ended only by verified webhooks. */
class PremiumIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var gateway: FakePaymentGateway

    @BeforeEach
    fun clean() = gateway.reset()

    private fun webhook(type: String, objectId: String, objectType: String): ResultActions {
        val (payload, signature) = FakePaymentGateway.signedEvent(type, objectId, objectType = objectType)
        return mockMvc.perform(
            post("/v1/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(payload).header("Stripe-Signature", signature),
        )
    }

    private fun status(session: Cookie) = mockMvc.perform(get("/v1/premium").cookie(session))

    private fun buy(session: Cookie, plan: PremiumPlan): String {
        val before = gateway.subscriptionCheckouts.keys.toSet()
        mockMvc.perform(post("/v1/premium/checkout").cookie(session).with(fromRandomIp()).json(mapOf("plan" to plan, "consent" to true)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.url").exists())
        return (gateway.subscriptionCheckouts.keys - before).single()
    }

    /** What Stripe does after the payment: the subscription exists, and the checkout webhook arrives. */
    private fun pay(sessionId: String, plan: PremiumPlan, days: Long = 30): SubscriptionState {
        val request = gateway.subscriptionCheckouts.getValue(sessionId)
        val subscription = "sub_${System.nanoTime()}"
        val state = SubscriptionState(
            id = subscription,
            customerId = request.customerId,
            userId = request.userId,
            plan = plan,
            status = "active",
            currentPeriodEnd = Instant.now().plus(Duration.ofDays(days)).truncatedTo(ChronoUnit.SECONDS),
            cancelAtPeriodEnd = false,
            endedAt = null,
        )
        gateway.subscriptions[subscription] = state
        gateway.completedCheckouts[sessionId] = subscription
        webhook("checkout.session.completed", sessionId, "checkout.session").andExpect(status().isOk)
        return state
    }

    @Test
    fun `a customer buys Premium monthly, cancels, and keeps it until the end of the paid period`() {
        val customer = loggedInUser()
        status(customer).andExpect(jsonPath("$.premium").value(false)).andExpect(jsonPath("$.freeScansLeft").value(100))

        mockMvc.perform(post("/v1/premium/checkout").cookie(customer).with(fromRandomIp()).json(mapOf("plan" to "MONTHLY")))
            .andExpect(status().isBadRequest)
        val session = buy(customer, PremiumPlan.MONTHLY)
        // The payment page alone changes nothing.
        status(customer).andExpect(jsonPath("$.premium").value(false))
        assertTrue(gateway.subscriptionCheckouts.getValue(session).consentText.contains("herroepingsrecht"))

        val state = pay(session, PremiumPlan.MONTHLY)
        status(customer)
            .andExpect(jsonPath("$.premium").value(true))
            .andExpect(jsonPath("$.reason").value("SUBSCRIPTION"))
            .andExpect(jsonPath("$.plan").value("MONTHLY"))
            .andExpect(jsonPath("$.nextPaymentAt").value(state.currentPeriodEnd.toString()))
            .andExpect(jsonPath("$.freeScansLeft").doesNotExist())
        mockMvc.perform(get("/v1/auth/me").cookie(customer)).andExpect(jsonPath("$.premium").value(true))
        mockMvc.perform(post("/v1/premium/checkout").cookie(customer).with(fromRandomIp()).json(mapOf("plan" to "YEARLY", "consent" to true)))
            .andExpect(status().isConflict)
        mockMvc.perform(post("/v1/premium/portal").cookie(customer).with(fromRandomIp()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.url").value("https://billing.stripe.test/${state.customerId}"))

        // Cancelled in the portal: Premium stays until the end of the period, and nothing is charged any more.
        gateway.subscriptions[state.id] = state.copy(cancelAtPeriodEnd = true)
        webhook("customer.subscription.updated", state.id, "subscription").andExpect(status().isOk)
        status(customer)
            .andExpect(jsonPath("$.premium").value(true))
            .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true))
            .andExpect(jsonPath("$.nextPaymentAt").doesNotExist())
            .andExpect(jsonPath("$.until").value(state.currentPeriodEnd.toString()))

        // At the end of the period Stripe ends it.
        gateway.subscriptions[state.id] = state.copy(status = "canceled", cancelAtPeriodEnd = true, endedAt = Instant.now())
        webhook("customer.subscription.deleted", state.id, "subscription").andExpect(status().isOk)
        status(customer).andExpect(jsonPath("$.premium").value(false))
    }

    @Test
    fun `a yearly subscription stops on a failed renewal and comes back when it's paid`() {
        val customer = loggedInUser()
        val state = pay(buy(customer, PremiumPlan.YEARLY), PremiumPlan.YEARLY, days = 365)
        status(customer).andExpect(jsonPath("$.plan").value("YEARLY")).andExpect(jsonPath("$.premium").value(true))

        gateway.invoices["in_failed_${state.id}"] = state.id
        gateway.subscriptions[state.id] = state.copy(status = "past_due")
        webhook("invoice.payment_failed", "in_failed_${state.id}", "invoice").andExpect(status().isOk)
        status(customer).andExpect(jsonPath("$.premium").value(false))

        gateway.invoices["in_paid_${state.id}"] = state.id
        gateway.subscriptions[state.id] = state.copy(status = "active")
        webhook("invoice.paid", "in_paid_${state.id}", "invoice").andExpect(status().isOk)
        status(customer).andExpect(jsonPath("$.premium").value(true))
    }

    @Test
    fun `a full refund ends Premium now and stops the subscription`() {
        val customer = loggedInUser()
        val state = pay(buy(customer, PremiumPlan.MONTHLY), PremiumPlan.MONTHLY)

        gateway.charges["ch_partial_${state.id}"] = RefundedCharge(state.customerId, fullyRefunded = false)
        webhook("charge.refunded", "ch_partial_${state.id}", "charge").andExpect(status().isOk)
        status(customer).andExpect(jsonPath("$.premium").value(true))

        gateway.charges["ch_${state.id}"] = RefundedCharge(state.customerId, fullyRefunded = true)
        webhook("charge.refunded", "ch_${state.id}", "charge").andExpect(status().isOk)
        status(customer).andExpect(jsonPath("$.premium").value(false))
        assertEquals(listOf(state.id), gateway.cancelled.filter { it == state.id })
    }

    @Test
    fun `an admin has Premium without buying it`() {
        val admin = loggedInUser(Role.ADMIN)

        status(admin).andExpect(jsonPath("$.premium").value(true)).andExpect(jsonPath("$.reason").value("ADMIN"))
        mockMvc.perform(post("/v1/premium/checkout").cookie(admin).with(fromRandomIp()).json(mapOf("plan" to "MONTHLY", "consent" to true)))
            .andExpect(status().isConflict)
    }

    @Test
    fun `a subscription webhook for a stranger changes nobody`() {
        gateway.subscriptions["sub_stranger"] = SubscriptionState("sub_stranger", "cus_unknown", null, PremiumPlan.MONTHLY, "active", Instant.now().plusSeconds(3600), false, null)
        webhook("customer.subscription.updated", "sub_stranger", "subscription").andExpect(status().isOk)
        mockMvc.perform(get("/v1/premium")).andExpect(status().isUnauthorized)
    }
}
