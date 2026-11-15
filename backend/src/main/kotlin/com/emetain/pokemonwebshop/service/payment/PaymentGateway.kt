package com.emetain.pokemonwebshop.service.payment

import java.time.Instant

/** A product line on the payment page. */
data class PaymentLine(val name: String, val unitAmountCents: Long, val quantity: Int, val imageUrl: String?)

/** A discount code on the payment page: [amountCents] off the products, never off shipping. */
data class PaymentDiscount(val code: String, val amountCents: Long)

data class CheckoutRequest(
    val orderNumber: String,
    val email: String,
    val lines: List<PaymentLine>,
    val shippingLabel: String,
    val shippingCents: Long,
    val paymentMethods: List<String>,
    val successUrl: String,
    val cancelUrl: String,
    val expiresAt: Instant,
    val discount: PaymentDiscount? = null,
)

data class CheckoutSession(val id: String, val url: String, val expiresAt: Instant)

/** What the payment provider says about a session right now. */
data class SessionState(
    val id: String,
    val paid: Boolean,
    val status: SessionStatus,
    val amountTotalCents: Long?,
    val currency: String?,
    val paymentIntentId: String?,
    val paymentMethod: String?,
)

enum class SessionStatus { OPEN, COMPLETE, EXPIRED }

/** The outcome of closing a payment page. */
enum class ExpireResult {
    /** Closed now or earlier: nobody can pay on it any more. */
    EXPIRED,

    /** The customer paid: the stock must stay. */
    COMPLETE,

    /** The provider doesn't know it, for example after a change of API keys. Nobody can pay on it either. */
    NOT_FOUND,
}

class PaymentUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * The payment provider, Stripe Checkout in practice. Behind an interface, so tests run without Stripe and the
 * provider could be swapped. Calls go over the network: never call these inside a database transaction that holds
 * a lock longer than needed.
 */
interface PaymentGateway {

    val configured: Boolean

    /**
     * Why online payment is off although a key is set, as a code for the admin dashboard (see [StripeKeyRules]).
     * Null when it is on, or when no key is set.
     */
    val setupProblem: String? get() = null

    fun createCheckout(request: CheckoutRequest): CheckoutSession

    fun retrieve(sessionId: String): SessionState

    fun expire(sessionId: String): ExpireResult

    /** Pays [amountCents] back. The same [idempotencyKey] never pays twice. Returns the provider's refund id. */
    fun refund(paymentIntentId: String, amountCents: Long, idempotencyKey: String, orderNumber: String): String
}
