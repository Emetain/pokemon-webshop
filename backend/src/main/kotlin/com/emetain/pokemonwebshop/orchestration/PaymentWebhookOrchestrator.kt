package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.emetain.pokemonwebshop.service.CheckoutService
import com.emetain.pokemonwebshop.service.OrderPaymentService
import com.emetain.pokemonwebshop.service.OrderService
import com.emetain.pokemonwebshop.service.PaymentOutcome
import com.emetain.pokemonwebshop.service.payment.PaymentGateway
import com.emetain.pokemonwebshop.service.payment.PaymentUnavailableException
import com.emetain.pokemonwebshop.service.payment.StripeWebhookVerifier
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

/**
 * Stripe's webhook. A payment only counts when Stripe signed the event and Stripe itself says the session is paid. The
 * page the customer returns to never changes an order. Stripe sends an event again when we answer with an error, so
 * a failure here throws.
 */
@Component
class PaymentWebhookOrchestrator(
    private val verifier: StripeWebhookVerifier,
    private val gateway: PaymentGateway,
    private val orderRepository: OrderRepository,
    private val orderPaymentService: OrderPaymentService,
    private val orderService: OrderService,
    private val checkoutService: CheckoutService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(payload: String, signature: String?) {
        val event = verifier.verify(payload, signature)
        val sessionId = event.objectId ?: return
        when (event.type) {
            "checkout.session.completed", "checkout.session.async_payment_succeeded" -> confirm(sessionId, event.id)
            "checkout.session.expired" -> ended(sessionId, "Betaalpagina verlopen")
            "checkout.session.async_payment_failed" -> failed(sessionId)
            else -> log.debug("Ignored Stripe event {} of type {}", event.id, event.type)
        }
    }

    private fun confirm(sessionId: String, eventId: String) {
        val order = orderRepository.findByStripeSessionId(sessionId) ?: return unknown(sessionId, eventId)
        val state = try {
            gateway.retrieve(sessionId)
        } catch (exception: PaymentUnavailableException) {
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "payments_unavailable", "Stripe is niet bereikbaar.")
        }
        if (!state.paid) {
            log.info("Checkout session {} of order {} completed, but isn't paid yet", sessionId, order.number)
            return
        }
        val outcome = retryingOnStockConflict { orderPaymentService.markPaid(order.id, state) }
        when (outcome) {
            PaymentOutcome.PAID -> log.info("Order {} is paid", order.number)
            PaymentOutcome.ALREADY_PAID -> log.debug("Order {} was already paid", order.number)
            PaymentOutcome.NEEDS_REFUND -> try {
                orderService.refundAutomatically(order.id)
                log.warn("Order {} was paid after its stock was gone and has been refunded", order.number)
            } catch (exception: ApiException) {
                log.error("Order {} was paid after its stock was gone, and the refund failed: refund it by hand", order.number)
            }
        }
    }

    private fun ended(sessionId: String, reason: String) {
        val order = orderRepository.findByStripeSessionId(sessionId) ?: return
        orderPaymentService.closePending(order.id, reason)
    }

    /** A slow payment method that failed after the customer finished checkout. Nobody can pay on the session any more. */
    private fun failed(sessionId: String) {
        val order = orderRepository.findByStripeSessionId(sessionId) ?: return
        checkoutService.releaseReservation(order.id, "Betaling mislukt")
    }

    /** A session from another shop or environment on the same Stripe account, such as a colleague's laptop. */
    private fun unknown(sessionId: String, eventId: String) {
        log.warn("Stripe event {} is about checkout session {}, which isn't ours", eventId, sessionId)
    }
}
