package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.orchestration.PaymentWebhookOrchestrator
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Stripe calls this. The body stays raw bytes, because the signature is over exactly those bytes. */
@Hidden
@RestController
@RequestMapping("/v1/payments/stripe")
class PaymentWebhookController(
    private val webhookOrchestrator: PaymentWebhookOrchestrator,
) {

    @PostMapping("/webhook")
    fun webhook(
        @RequestBody payload: ByteArray,
        @RequestHeader("Stripe-Signature", required = false) signature: String?,
    ): ResponseEntity<Void> {
        webhookOrchestrator.handle(String(payload, Charsets.UTF_8), signature)
        return ResponseEntity.ok().build()
    }
}
