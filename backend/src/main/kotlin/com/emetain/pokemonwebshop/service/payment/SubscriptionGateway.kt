package com.emetain.pokemonwebshop.service.payment

import com.emetain.pokemonwebshop.domain.PremiumPlan
import java.time.Instant

data class SubscriptionCheckoutRequest(
    val userId: Long,
    val customerId: String,
    val plan: PremiumPlan,
    val priceCents: Long,
    val successUrl: String,
    val cancelUrl: String,
    /** Shown above the pay button: Premium starts right away and the withdrawal right ends with that. */
    val consentText: String,
)

data class SubscriptionCheckout(val sessionId: String, val url: String)

/** A finished Premium checkout and the subscription it made. */
data class CheckoutSubscription(val subscriptionId: String, val customerId: String?, val userId: Long?)

/** What the payment provider says about a subscription right now. */
data class SubscriptionState(
    val id: String,
    val customerId: String,
    val userId: Long?,
    val plan: PremiumPlan?,
    val status: String,
    val currentPeriodEnd: Instant?,
    val cancelAtPeriodEnd: Boolean,
    val endedAt: Instant?,
) {
    /** Paid up: Stripe's `active` and `trialing`. A failed renewal is `past_due` and isn't. */
    val active: Boolean get() = status == "active" || status == "trialing"
}

data class RefundedCharge(val customerId: String?, val fullyRefunded: Boolean)

/**
 * Premium subscriptions at the payment provider, Stripe Billing in practice (phase 11). Next to [PaymentGateway] and
 * implemented by the same class, so orders and Premium share one account, one key and one webhook. Every answer
 * here is the provider's own, asked for after a verified webhook: a webhook's body alone never changes Premium.
 */
interface SubscriptionGateway {

    val configured: Boolean

    /** A customer for the user, so the provider's customer portal can show their subscription. */
    fun createCustomer(userId: Long, email: String): String

    fun createSubscriptionCheckout(request: SubscriptionCheckoutRequest): SubscriptionCheckout

    /** Null when the session isn't a Premium checkout of this shop, or isn't finished. */
    fun subscriptionOfCheckout(sessionId: String): CheckoutSubscription?

    fun retrieveSubscription(subscriptionId: String): SubscriptionState

    /** The subscription an invoice belongs to, or null for an invoice without one. */
    fun subscriptionOfInvoice(invoiceId: String): String?

    fun refundedCharge(chargeId: String): RefundedCharge

    /** Ends the subscription now, for example after a full refund. */
    fun cancelSubscription(subscriptionId: String)

    /** A page at the provider where the customer cancels or switches between monthly and yearly. */
    fun portalUrl(customerId: String, returnUrl: String): String
}
