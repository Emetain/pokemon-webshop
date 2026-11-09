package com.emetain.pokemonwebshop.service.payment

import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.service.testProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.stripe.net.Webhook
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class StripeWebhookVerifierTest {

    private val secret = "whsec_unit"
    private val properties = testProperties().copy(
        payments = com.emetain.pokemonwebshop.config.AppProperties.Payments(
            com.emetain.pokemonwebshop.config.AppProperties.Stripe(secretKey = "sk_test_x", webhookSecret = secret),
        ),
    )
    private val verifier = StripeWebhookVerifier(properties, ObjectMapper())
    private val payload = """{"id":"evt_1","type":"checkout.session.completed","data":{"object":{"id":"cs_test_1"}}}"""

    private fun sign(body: String, timestamp: Long = Webhook.Util.getTimeNow(), key: String = secret) =
        "t=$timestamp,v1=${Webhook.Util.computeHmacSha256(key, "$timestamp.$body")}"

    @Test
    fun `a signed event gives its type and session`() {
        val event = verifier.verify(payload, sign(payload))

        assertEquals(PaymentEvent("evt_1", "checkout.session.completed", "cs_test_1"), event)
    }

    @Test
    fun `a missing, wrong or old signature is refused`() {
        listOf(
            null,
            "",
            sign(payload, key = "whsec_someone_else"),
            sign(payload, timestamp = Webhook.Util.getTimeNow() - 600),
            sign(payload.replace("cs_test_1", "cs_test_2")),
        ).forEach { signature ->
            val error = assertThrows<ApiException>("signature $signature") { verifier.verify(payload, signature) }
            assertEquals("invalid_signature", error.code)
        }
    }

    @Test
    fun `a changed body no longer matches its signature`() {
        val signature = sign(payload)
        val changed = payload.replace("cs_test_1", "cs_test_9")

        assertThrows<ApiException> { verifier.verify(changed, signature) }
    }

    @Test
    fun `without a webhook secret nothing is accepted`() {
        val unconfigured = StripeWebhookVerifier(testProperties(), ObjectMapper())

        val error = assertThrows<ApiException> { unconfigured.verify(payload, sign(payload)) }
        assertEquals("webhook_not_configured", error.code)
    }
}
