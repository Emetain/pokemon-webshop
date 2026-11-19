package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.PremiumPlan
import com.emetain.pokemonwebshop.domain.PremiumSource
import com.emetain.pokemonwebshop.domain.PremiumSubscription
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.PremiumSubscriptionRepository
import com.emetain.pokemonwebshop.repository.UserRepository
import com.emetain.pokemonwebshop.service.payment.PaymentUnavailableException
import com.emetain.pokemonwebshop.service.payment.SubscriptionCheckoutRequest
import com.emetain.pokemonwebshop.service.payment.SubscriptionGateway
import com.emetain.pokemonwebshop.service.payment.SubscriptionState
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant

/** Why a user has Premium, or doesn't. */
enum class PremiumReason { ADMIN, FOREVER, SUBSCRIPTION, GRANT, NONE }

/** Who has Premium (phase 11). Pure, so every Premium feature asks the same question the same way. */
object PremiumAccess {

    fun has(user: User?, now: Instant): Boolean = reason(user, now) != PremiumReason.NONE

    fun reason(user: User?, now: Instant): PremiumReason = when {
        user == null -> PremiumReason.NONE
        // Admins always have it, decided from the role and never stored.
        user.role == Role.ADMIN -> PremiumReason.ADMIN
        user.premiumForever -> PremiumReason.FOREVER
        user.premiumUntil?.isAfter(now) == true ->
            if (user.premiumSource == PremiumSource.GRANT) PremiumReason.GRANT else PremiumReason.SUBSCRIPTION
        else -> PremiumReason.NONE
    }

    /**
     * Works out a user's Premium from all their subscriptions as Stripe last described them. A paid-up subscription
     * gives Premium until the end of its period; a cancelled one keeps it until then and Stripe ends it there. A
     * failed renewal (`past_due`) or a subscription that ended takes it away now. A grant for good is left alone.
     */
    fun recompute(user: User, subscriptions: List<PremiumSubscription>, now: Instant) {
        if (user.premiumForever) return
        val hadPremium = user.premiumUntil?.isAfter(now) == true
        val paidUntil = subscriptions.filter { it.status in ACTIVE }.mapNotNull { it.currentPeriodEnd }.maxOrNull()
        if (paidUntil != null && paidUntil.isAfter(now)) {
            if (!hadPremium) user.premiumSince = now
            user.premiumUntil = paidUntil
            user.premiumSource = PremiumSource.SUBSCRIPTION
        } else if (user.premiumSource == PremiumSource.SUBSCRIPTION && hadPremium) {
            user.premiumUntil = now
        }
    }

    val ACTIVE = setOf("active", "trialing")
}

data class PremiumStatus(
    val premium: Boolean,
    val reason: PremiumReason,
    val since: Instant?,
    val until: Instant?,
    val plan: PremiumPlan?,
    /** Stripe's status of the latest subscription, if there is one. */
    val subscriptionStatus: String?,
    val cancelAtPeriodEnd: Boolean,
    /** When Stripe charges next: the end of the period, unless the customer cancelled. */
    val nextPaymentAt: Instant?,
    val canManage: Boolean,
    val monthlyPriceCents: Long,
    val yearlyPriceCents: Long,
    val freeScans: Int,
    val freeScansLeft: Int?,
)

/**
 * Premium as a Stripe subscription (phase 11). Buying starts here; only Stripe's verified webhooks switch it on,
 * renew it and end it, after asking Stripe for the subscription itself. Calls to Stripe never happen inside a
 * database transaction.
 */
@Service
class PremiumService(
    private val userRepository: UserRepository,
    private val subscriptionRepository: PremiumSubscriptionRepository,
    private val gateway: SubscriptionGateway,
    private val transaction: TransactionTemplate,
    private val salesGate: SalesGate,
    private val clock: Clock,
    properties: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val premium = properties.premium
    private val baseUrl = properties.baseUrl.trimEnd('/')

    fun has(user: User?): Boolean = PremiumAccess.has(user, clock.instant())

    /** Refuses a Premium feature to a customer without it. The server checks; hiding a button isn't enough. */
    fun require(user: User?, message: String = "Dit hoort bij Premium.") {
        if (user == null) throw ApiException.notAuthenticated()
        if (!has(user)) throw ApiException.premiumRequired(message)
    }

    fun status(user: User): PremiumStatus {
        val fresh = userRepository.findById(user.id).orElse(user)
        val now = clock.instant()
        val reason = PremiumAccess.reason(fresh, now)
        val subscription = subscriptionRepository.findFirstByUserIdOrderByUpdatedAtDescIdDesc(fresh.id)
        val active = subscription?.takeIf { it.status in PremiumAccess.ACTIVE && reason == PremiumReason.SUBSCRIPTION }
        return PremiumStatus(
            premium = reason != PremiumReason.NONE,
            reason = reason,
            since = fresh.premiumSince.takeIf { reason == PremiumReason.SUBSCRIPTION || reason == PremiumReason.GRANT },
            until = fresh.premiumUntil.takeIf { reason == PremiumReason.SUBSCRIPTION || reason == PremiumReason.GRANT },
            plan = active?.plan,
            subscriptionStatus = subscription?.status,
            cancelAtPeriodEnd = active?.cancelAtPeriodEnd == true,
            nextPaymentAt = active?.takeIf { !it.cancelAtPeriodEnd }?.currentPeriodEnd,
            canManage = fresh.stripeCustomerId != null && gateway.configured,
            monthlyPriceCents = premium.monthlyPriceCents,
            yearlyPriceCents = premium.yearlyPriceCents,
            freeScans = premium.freeScans,
            freeScansLeft = if (reason == PremiumReason.NONE) (premium.freeScans - fresh.freeScansUsed).coerceAtLeast(0) else null,
        )
    }

    /** Starts Stripe's payment page for Premium. Returns its address. */
    fun startCheckout(user: User, plan: PremiumPlan, consent: Boolean): String {
        // Online without the preview login but not open yet, only admins may pay, and they have Premium already.
        salesGate.requireCanOrder(user)
        if (!consent) throw ApiException.validation("Geef toestemming dat Premium direct begint.")
        if (!gateway.configured) {
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "payments_unavailable", "Online betalen staat nog uit.")
        }
        val fresh = userRepository.findById(user.id).orElseThrow { ApiException.notAuthenticated() }
        when (PremiumAccess.reason(fresh, clock.instant())) {
            PremiumReason.ADMIN -> throw conflict("Als beheerder heb je Premium al.")
            PremiumReason.NONE -> Unit
            else -> throw conflict("Je hebt Premium al. Beheer je abonnement onder Mijn account.")
        }

        val customerId = fresh.stripeCustomerId ?: unavailable { gateway.createCustomer(fresh.id, fresh.email) }
        transaction.executeWithoutResult {
            val locked = userRepository.findByIdForUpdate(fresh.id) ?: return@executeWithoutResult
            if (locked.stripeCustomerId == null) locked.stripeCustomerId = customerId
            locked.premiumConsentAt = clock.instant()
        }
        val checkout = unavailable {
            gateway.createSubscriptionCheckout(
                SubscriptionCheckoutRequest(
                    userId = fresh.id,
                    customerId = customerId,
                    plan = plan,
                    priceCents = if (plan == PremiumPlan.MONTHLY) premium.monthlyPriceCents else premium.yearlyPriceCents,
                    successUrl = "$baseUrl/premium?betaald=1",
                    cancelUrl = "$baseUrl/premium",
                    consentText = CONSENT_TEXT,
                ),
            )
        }
        return checkout.url
    }

    /** Stripe's customer portal, where the customer cancels or switches between monthly and yearly. */
    fun portal(user: User): String {
        val fresh = userRepository.findById(user.id).orElseThrow { ApiException.notAuthenticated() }
        val customerId = fresh.stripeCustomerId ?: throw ApiException.notFound("Je hebt nog geen abonnement.")
        return unavailable { gateway.portalUrl(customerId, "$baseUrl/account") }
    }

    // Webhooks -------------------------------------------------------------------------------------------------------

    /** A finished checkout. Returns false when it wasn't a Premium checkout of this shop. */
    fun checkoutCompleted(sessionId: String): Boolean {
        val checkout = webhook { gateway.subscriptionOfCheckout(sessionId) } ?: return false
        syncSubscription(checkout.subscriptionId)
        return true
    }

    /** Created, renewed, switched, cancelled, failed or ended: asks Stripe and applies what it says. */
    fun syncSubscription(subscriptionId: String) {
        val state = webhook { gateway.retrieveSubscription(subscriptionId) }
        transaction.executeWithoutResult { apply(state) }
    }

    fun syncInvoice(invoiceId: String) {
        val subscriptionId = webhook { gateway.subscriptionOfInvoice(invoiceId) } ?: return
        syncSubscription(subscriptionId)
    }

    /** A full refund of a Premium payment ends Premium now and stops the subscription. */
    fun chargeRefunded(chargeId: String) {
        val charge = webhook { gateway.refundedCharge(chargeId) }
        if (!charge.fullyRefunded || charge.customerId == null) return
        val user = userRepository.findByStripeCustomerId(charge.customerId) ?: return
        val running = subscriptionRepository.findAllByUserId(user.id).filter { it.status in PremiumAccess.ACTIVE }
        running.forEach { webhook { gateway.cancelSubscription(it.stripeSubscriptionId) } }
        transaction.executeWithoutResult {
            val locked = userRepository.findByIdForUpdate(user.id) ?: return@executeWithoutResult
            val now = clock.instant()
            subscriptionRepository.findAllByUserId(locked.id).filter { it.status in PremiumAccess.ACTIVE }.forEach {
                it.status = "canceled"
                it.updatedAt = now
            }
            if (locked.premiumSource == PremiumSource.SUBSCRIPTION && locked.premiumUntil?.isAfter(now) == true) {
                locked.premiumUntil = now
            }
            log.info("Premium of user {} ended after a full refund", locked.id)
        }
    }

    private fun apply(state: SubscriptionState) {
        val owner = state.userId?.let { userRepository.findById(it).orElse(null) }
            ?.takeIf { it.stripeCustomerId == null || it.stripeCustomerId == state.customerId }
            ?: userRepository.findByStripeCustomerId(state.customerId)
        if (owner == null) {
            log.warn("Subscription {} of customer {} belongs to no user of this shop", state.id, state.customerId)
            return
        }
        val user = userRepository.findByIdForUpdate(owner.id) ?: return
        val now = clock.instant()
        val subscription = subscriptionRepository.findByStripeSubscriptionId(state.id)
            ?: subscriptionRepository.save(
                PremiumSubscription(
                    user = user,
                    stripeSubscriptionId = state.id,
                    status = state.status,
                    consentAt = user.premiumConsentAt,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        subscription.plan = state.plan ?: subscription.plan
        subscription.status = state.status
        subscription.currentPeriodEnd = state.currentPeriodEnd
        subscription.cancelAtPeriodEnd = state.cancelAtPeriodEnd
        subscription.updatedAt = now
        if (user.stripeCustomerId == null) user.stripeCustomerId = state.customerId
        PremiumAccess.recompute(user, subscriptionRepository.findAllByUserId(user.id), now)
        log.info("Subscription {} of user {} is {}; Premium until {}", state.id, user.id, state.status, user.premiumUntil)
    }

    private fun conflict(message: String) = ApiException(HttpStatus.CONFLICT, "premium_exists", message)

    private fun <T> unavailable(action: () -> T): T = try {
        action()
    } catch (exception: PaymentUnavailableException) {
        throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "payments_unavailable", "Stripe is even niet bereikbaar. Probeer het zo opnieuw.")
    }

    /** Stripe sends a webhook again when it gets an error back, so a failed call to Stripe must fail the webhook. */
    private fun <T> webhook(action: () -> T): T = try {
        action()
    } catch (exception: PaymentUnavailableException) {
        throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "payments_unavailable", "Stripe is niet bereikbaar.")
    }

    private companion object {
        const val CONSENT_TEXT = "Premium begint direct na je betaling. Je hebt ermee ingestemd dat je herroepingsrecht " +
            "dan vervalt. Opzeggen kan altijd onder Mijn account, tegen het einde van de betaalde periode."
    }
}
