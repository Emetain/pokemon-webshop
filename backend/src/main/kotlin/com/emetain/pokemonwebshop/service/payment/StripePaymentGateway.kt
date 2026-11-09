package com.emetain.pokemonwebshop.service.payment

import com.emetain.pokemonwebshop.config.AppProperties
import com.stripe.StripeClient
import com.stripe.exception.InvalidRequestException
import com.stripe.exception.StripeException
import com.stripe.model.checkout.Session
import com.stripe.net.RequestOptions
import com.stripe.param.RefundCreateParams
import com.stripe.param.checkout.SessionCreateParams
import com.stripe.param.checkout.SessionRetrieveParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Stripe Checkout. The library pins its own API version (`2025-05-28.basil` for stripe-java 29.2.0), so every call
 * here speaks that version whatever the account's default is.
 */
@Component
class StripePaymentGateway(properties: AppProperties) : PaymentGateway {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secretKey = properties.payments.stripe.secretKey
    private val client: StripeClient? = secretKey.takeIf { it.isNotBlank() }?.let(::StripeClient)

    override val configured: Boolean get() = client != null

    override fun createCheckout(request: CheckoutRequest): CheckoutSession {
        val params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setLocale(SessionCreateParams.Locale.NL)
            .setCustomerEmail(request.email)
            .setClientReferenceId(request.orderNumber)
            .putMetadata(ORDER_NUMBER, request.orderNumber)
            .setPaymentIntentData(
                SessionCreateParams.PaymentIntentData.builder()
                    .setDescription("Bestelling ${request.orderNumber}")
                    .putMetadata(ORDER_NUMBER, request.orderNumber)
                    .build(),
            )
            .setSuccessUrl(request.successUrl)
            .setCancelUrl(request.cancelUrl)
            .setExpiresAt(request.expiresAt.epochSecond)
            .addShippingOption(
                SessionCreateParams.ShippingOption.builder()
                    .setShippingRateData(
                        SessionCreateParams.ShippingOption.ShippingRateData.builder()
                            .setType(SessionCreateParams.ShippingOption.ShippingRateData.Type.FIXED_AMOUNT)
                            .setDisplayName(request.shippingLabel)
                            .setFixedAmount(
                                SessionCreateParams.ShippingOption.ShippingRateData.FixedAmount.builder()
                                    .setAmount(request.shippingCents)
                                    .setCurrency(CURRENCY)
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
        request.paymentMethods.forEach { params.addPaymentMethodType(paymentMethodType(it)) }
        request.lines.forEach { line ->
            val product = SessionCreateParams.LineItem.PriceData.ProductData.builder().setName(line.name)
            // Stripe shows the picture if it can reach it. Our own uploads are relative links, card images aren't.
            line.imageUrl?.takeIf { it.startsWith("https://") }?.let(product::addImage)
            params.addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(line.quantity.toLong())
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(CURRENCY)
                            .setUnitAmount(line.unitAmountCents)
                            .setProductData(product.build())
                            .build(),
                    )
                    .build(),
            )
        }
        val options = RequestOptions.builder().setIdempotencyKey("checkout-${request.orderNumber}").build()
        val session = call("create a checkout session") { stripe().checkout().sessions().create(params.build(), options) }
        return CheckoutSession(session.id, session.url, Instant.ofEpochSecond(session.expiresAt))
    }

    override fun retrieve(sessionId: String): SessionState {
        val params = SessionRetrieveParams.builder().addExpand("payment_intent.latest_charge").build()
        val session = call("retrieve a checkout session") { stripe().checkout().sessions().retrieve(sessionId, params) }
        return state(session)
    }

    override fun expire(sessionId: String): ExpireResult {
        return try {
            stripe().checkout().sessions().expire(sessionId)
            ExpireResult.EXPIRED
        } catch (exception: InvalidRequestException) {
            if (exception.statusCode == NOT_FOUND || exception.code == "resource_missing") return ExpireResult.NOT_FOUND
            // Only an open session can be expired. Ask what it is instead.
            when (retrieve(sessionId).status) {
                SessionStatus.COMPLETE -> ExpireResult.COMPLETE
                SessionStatus.EXPIRED -> ExpireResult.EXPIRED
                SessionStatus.OPEN -> throw PaymentUnavailableException("Stripe refused to expire $sessionId", exception)
            }
        } catch (exception: StripeException) {
            throw PaymentUnavailableException("Could not expire checkout session $sessionId", exception)
        }
    }

    override fun refund(paymentIntentId: String, amountCents: Long, idempotencyKey: String, orderNumber: String): String {
        val params = RefundCreateParams.builder()
            .setPaymentIntent(paymentIntentId)
            .setAmount(amountCents)
            .putMetadata(ORDER_NUMBER, orderNumber)
            .build()
        val options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build()
        return call("refund $orderNumber") { stripe().refunds().create(params, options) }.id
    }

    private fun state(session: Session): SessionState {
        val paymentIntent = session.paymentIntentObject
        return SessionState(
            id = session.id,
            paid = session.paymentStatus == "paid",
            status = when (session.status) {
                "complete" -> SessionStatus.COMPLETE
                "expired" -> SessionStatus.EXPIRED
                else -> SessionStatus.OPEN
            },
            amountTotalCents = session.amountTotal,
            currency = session.currency,
            paymentIntentId = paymentIntent?.id ?: session.paymentIntent,
            paymentMethod = paymentIntent?.latestChargeObject?.paymentMethodDetails?.type,
        )
    }

    private fun stripe(): StripeClient = client ?: throw PaymentUnavailableException("STRIPE_SECRET_KEY is not set")

    private fun <T> call(what: String, action: () -> T): T = try {
        action()
    } catch (exception: StripeException) {
        log.warn("Stripe could not {}: {} ({})", what, exception.message, exception.requestId)
        throw PaymentUnavailableException("Stripe could not $what", exception)
    }

    private fun paymentMethodType(method: String): SessionCreateParams.PaymentMethodType = when (method) {
        "ideal" -> SessionCreateParams.PaymentMethodType.IDEAL
        "bancontact" -> SessionCreateParams.PaymentMethodType.BANCONTACT
        "card" -> SessionCreateParams.PaymentMethodType.CARD
        else -> throw IllegalArgumentException("Unknown payment method $method")
    }

    private companion object {
        const val CURRENCY = "eur"
        const val ORDER_NUMBER = "order_number"
        const val NOT_FOUND = 404
    }
}
