package com.emetain.pokemonwebshop.service.payment

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.exception.ApiException
import com.fasterxml.jackson.databind.ObjectMapper
import com.stripe.exception.SignatureVerificationException
import com.stripe.net.Webhook
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

/** The part of a Stripe event the shop needs. */
data class PaymentEvent(val id: String, val type: String, val objectId: String?)

/**
 * Checks that a webhook comes from Stripe: the signature over the raw body with the webhook secret, and a timestamp of
 * at most five minutes ago, so an old event can't be replayed. Only then is the body read. The event's own data is
 * not trusted beyond its type and id: the shop asks Stripe for the session itself.
 */
@Component
class StripeWebhookVerifier(
    properties: AppProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val secret = properties.payments.stripe.webhookSecret

    fun verify(payload: String, signature: String?): PaymentEvent {
        if (secret.isBlank()) {
            log.error("A Stripe webhook arrived, but STRIPE_WEBHOOK_SECRET is not set")
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "webhook_not_configured", "Webhooks zijn niet ingesteld.")
        }
        if (signature.isNullOrBlank()) throw invalid()
        try {
            Webhook.Signature.verifyHeader(payload, signature, secret, Webhook.DEFAULT_TOLERANCE)
        } catch (exception: SignatureVerificationException) {
            log.warn("Rejected a Stripe webhook: {}", exception.message)
            throw invalid()
        }
        val event = runCatching { objectMapper.readTree(payload) }.getOrNull() ?: throw invalid()
        val id = event.path("id").asText()
        val type = event.path("type").asText()
        if (id.isBlank() || type.isBlank()) throw invalid()
        val objectId = event.path("data").path("object").path("id").asText().takeIf { it.isNotBlank() }
        return PaymentEvent(id, type, objectId)
    }

    private fun invalid() =
        ApiException(HttpStatus.BAD_REQUEST, "invalid_signature", "Deze webhook is niet van Stripe.")
}
