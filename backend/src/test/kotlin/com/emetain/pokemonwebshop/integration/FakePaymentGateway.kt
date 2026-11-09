package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.service.payment.CheckoutRequest
import com.emetain.pokemonwebshop.service.payment.CheckoutSession
import com.emetain.pokemonwebshop.service.payment.ExpireResult
import com.emetain.pokemonwebshop.service.payment.PaymentGateway
import com.emetain.pokemonwebshop.service.payment.PaymentUnavailableException
import com.emetain.pokemonwebshop.service.payment.SessionState
import com.emetain.pokemonwebshop.service.payment.SessionStatus
import com.stripe.net.Webhook
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Stands in for Stripe. A session is paid as soon as a test says so, and every call is kept so tests can check it. */
class FakePaymentGateway : PaymentGateway {

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
            amountTotalCents = request.lines.sumOf { it.unitAmountCents * it.quantity } + request.shippingCents,
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

    companion object {
        const val WEBHOOK_SECRET = "whsec_test_secret"

        fun sessionId(orderNumber: String) = "cs_test_$orderNumber"

        /** A Stripe event signed like Stripe does: HMAC-SHA256 over "timestamp.payload". */
        fun signedEvent(type: String, sessionId: String, eventId: String = "evt_${System.nanoTime()}", secret: String = WEBHOOK_SECRET): Pair<String, String> {
            val payload = """{"id":"$eventId","object":"event","type":"$type","data":{"object":{"id":"$sessionId","object":"checkout.session"}}}"""
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
