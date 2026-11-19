package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.service.payment.CheckoutRequest
import com.emetain.pokemonwebshop.service.payment.CheckoutSession
import com.emetain.pokemonwebshop.service.payment.ExpireResult
import com.emetain.pokemonwebshop.service.payment.PaymentGateway
import com.emetain.pokemonwebshop.service.payment.PaymentUnavailableException
import com.emetain.pokemonwebshop.service.payment.SessionState
import com.emetain.pokemonwebshop.service.payment.SessionStatus
import com.emetain.pokemonwebshop.service.payment.CheckoutSubscription
import com.emetain.pokemonwebshop.service.payment.RefundedCharge
import com.emetain.pokemonwebshop.service.payment.SubscriptionCheckout
import com.emetain.pokemonwebshop.service.payment.SubscriptionCheckoutRequest
import com.emetain.pokemonwebshop.service.payment.SubscriptionGateway
import com.emetain.pokemonwebshop.service.payment.SubscriptionState
import com.stripe.net.Webhook
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Stands in for Stripe. A session is paid as soon as a test says so, and every call is kept so tests can check it. */
class FakePaymentGateway : PaymentGateway, SubscriptionGateway {

    data class Refund(val paymentIntentId: String, val amountCents: Long, val idempotencyKey: String)

    val sessions = ConcurrentHashMap<String, CheckoutRequest>()
    val paid = ConcurrentHashMap.newKeySet<String>()
    val expired = CopyOnWriteArrayList<String>()
    val refunds = CopyOnWriteArrayList<Refund>()

    /** What [expire] answers per session, for tests of a payment that came in just before. */
    val expireResults = ConcurrentHashMap<String, ExpireResult>()

    @Volatile var failCreate = false

    @Volatile var failRefund = false

    override val configured: Boolean get() = true

    override fun createCheckout(request: CheckoutRequest): CheckoutSession {
        if (failCreate) throw PaymentUnavailableException("Stripe is down in this test")
        val id = sessionId(request.orderNumber)
        sessions[id] = request
        return CheckoutSession(id, "https://checkout.stripe.test/$id", request.expiresAt)
    }

    override fun retrieve(sessionId: String): SessionState {
        val request = sessions[sessionId] ?: throw PaymentUnavailableException("No such session $sessionId")
        val isPaid = sessionId in paid
        return SessionState(
            id = sessionId,
            paid = isPaid,
            status = when {
                isPaid -> SessionStatus.COMPLETE
                sessionId in expired -> SessionStatus.EXPIRED
                else -> SessionStatus.OPEN
            },
            amountTotalCents = request.lines.sumOf { it.unitAmountCents * it.quantity } - (request.discount?.amountCents ?: 0) +
                request.shippingCents,
            currency = "eur",
            paymentIntentId = "pi_$sessionId",
            paymentMethod = request.paymentMethods.first(),
        )
    }

    override fun expire(sessionId: String): ExpireResult {
        expireResults[sessionId]?.let { return it }
        if (sessionId in paid) return ExpireResult.COMPLETE
        expired += sessionId
        return ExpireResult.EXPIRED
    }

    override fun refund(paymentIntentId: String, amountCents: Long, idempotencyKey: String, orderNumber: String): String {
        if (failRefund) throw PaymentUnavailableException("Refunds fail in this test")
        refunds.firstOrNull { it.idempotencyKey == idempotencyKey }?.let { return "re_${it.idempotencyKey}" }
        refunds += Refund(paymentIntentId, amountCents, idempotencyKey)
        return "re_$idempotencyKey"
    }

    fun reset() {
        failCreate = false
        failRefund = false
        expireResults.clear()
    }

    // Premium (phase 11) ---------------------------------------------------------------------------------------------

    val customers = ConcurrentHashMap<String, Long>()
    val subscriptionCheckouts = ConcurrentHashMap<String, SubscriptionCheckoutRequest>()

    /** What Stripe would answer about each subscription; a test changes it, then sends a webhook. */
    val subscriptions = ConcurrentHashMap<String, SubscriptionState>()

    /** Finished Premium checkouts: session id to subscription id. */
    val completedCheckouts = ConcurrentHashMap<String, String>()
    val invoices = ConcurrentHashMap<String, String>()
    val charges = ConcurrentHashMap<String, RefundedCharge>()
    val cancelled = CopyOnWriteArrayList<String>()

    override fun createCustomer(userId: Long, email: String): String =
        "cus_test_$userId".also { customers[it] = userId }

    override fun createSubscriptionCheckout(request: SubscriptionCheckoutRequest): SubscriptionCheckout {
        val id = "cs_test_premium_${request.userId}_${System.nanoTime()}"
        subscriptionCheckouts[id] = request
        return SubscriptionCheckout(id, "https://checkout.stripe.test/$id")
    }

    override fun subscriptionOfCheckout(sessionId: String): CheckoutSubscription? {
        val subscription = completedCheckouts[sessionId] ?: return null
        val request = subscriptionCheckouts[sessionId]
        return CheckoutSubscription(subscription, request?.customerId, request?.userId)
    }

    override fun retrieveSubscription(subscriptionId: String): SubscriptionState =
        subscriptions[subscriptionId] ?: throw PaymentUnavailableException("No such subscription $subscriptionId")

    override fun subscriptionOfInvoice(invoiceId: String): String? = invoices[invoiceId]

    override fun refundedCharge(chargeId: String): RefundedCharge =
        charges[chargeId] ?: throw PaymentUnavailableException("No such charge $chargeId")

    override fun cancelSubscription(subscriptionId: String) {
        cancelled += subscriptionId
    }

    override fun portalUrl(customerId: String, returnUrl: String): String = "https://billing.stripe.test/$customerId"

    companion object {
        const val WEBHOOK_SECRET = "whsec_test_secret"

        fun sessionId(orderNumber: String) = "cs_test_$orderNumber"

        /** A Stripe event signed like Stripe does: HMAC-SHA256 over "timestamp.payload". */
        fun signedEvent(
            type: String,
            sessionId: String,
            eventId: String = "evt_${System.nanoTime()}",
            secret: String = WEBHOOK_SECRET,
            objectType: String = "checkout.session",
        ): Pair<String, String> {
            val payload = """{"id":"$eventId","object":"event","type":"$type","data":{"object":{"id":"$sessionId","object":"$objectType"}}}"""
            val timestamp = Webhook.Util.getTimeNow()
            val signature = Webhook.Util.computeHmacSha256(secret, "$timestamp.$payload")
            return payload to "t=$timestamp,v1=$signature"
        }
    }
}

@TestConfiguration
class TestPaymentConfig {
    @Bean
    @Primary
    fun fakePaymentGateway() = FakePaymentGateway()
}
