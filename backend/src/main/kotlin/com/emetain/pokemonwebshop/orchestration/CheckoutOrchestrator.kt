package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CheckoutCountryDto
import com.emetain.pokemonwebshop.dto.CheckoutOptionsDto
import com.emetain.pokemonwebshop.dto.CheckoutStartDto
import com.emetain.pokemonwebshop.dto.StartCheckoutRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.emetain.pokemonwebshop.service.CartService
import com.emetain.pokemonwebshop.service.CheckoutService
import com.emetain.pokemonwebshop.service.OrderPaymentService
import com.emetain.pokemonwebshop.service.PaymentMethods
import com.emetain.pokemonwebshop.service.ShippingRateService
import com.emetain.pokemonwebshop.service.payment.CheckoutRequest
import com.emetain.pokemonwebshop.service.payment.PaymentGateway
import com.emetain.pokemonwebshop.service.payment.PaymentLine
import com.emetain.pokemonwebshop.service.payment.PaymentUnavailableException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

@Component
class CheckoutOrchestrator(
    private val cartOrchestrator: CartOrchestrator,
    private val cartService: CartService,
    private val checkoutService: CheckoutService,
    private val orderPaymentService: OrderPaymentService,
    private val shippingRateService: ShippingRateService,
    private val orderRepository: OrderRepository,
    private val gateway: PaymentGateway,
    private val properties: AppProperties,
    private val clock: Clock,
) {

    fun options(): CheckoutOptionsDto = CheckoutOptionsDto(
        paymentsEnabled = gateway.configured,
        countries = shippingRateService.all().filter { it.active }.map {
            CheckoutCountryDto(it.country, it.priceCents, it.freeFromCents, it.deliveryTime, PaymentMethods.forCountry(it.country))
        },
        reservationMinutes = properties.checkout.reservationTtl.toMinutes(),
    )

    /**
     * Reserves the stock, makes the order and opens the payment page at Stripe. An earlier checkout of the same cart
     * that wasn't paid is closed first, so a customer who goes back and tries again doesn't block their own stock.
     */
    fun start(viewer: User?, httpRequest: HttpServletRequest, request: StartCheckoutRequest): CheckoutStartDto {
        if (!gateway.configured) {
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "payments_unavailable", "Online betalen is nog niet ingesteld. Probeer het later opnieuw.")
        }
        val cart = cartService.find(cartOrchestrator.owner(viewer, httpRequest))
            ?: throw ApiException.validation("Je winkelwagen is leeg.")
        for (orderId in orderRepository.findIdsByCartAndStatus(cart.id)) {
            orderPaymentService.closePending(orderId, "Vervangen door een nieuwe poging om af te rekenen")
        }
        val order = retryingOnStockConflict { checkoutService.createOrder(cart.id, viewer, request) }
        val session = try {
            gateway.createCheckout(paymentRequest(order))
        } catch (exception: PaymentUnavailableException) {
            checkoutService.releaseReservation(order.id, "De betaalpagina kon niet worden geopend")
            throw ApiException(HttpStatus.BAD_GATEWAY, "payments_unavailable", "We konden de betaalpagina niet openen. Probeer het over een paar minuten opnieuw.")
        }
        checkoutService.attachSession(order.id, session)
        return CheckoutStartDto(order.number, order.accessToken, session.url)
    }

    /** What Stripe gets: the lines at the prices of the order, and where the customer comes back to. */
    fun paymentRequest(order: Order, dropSlug: String? = null): CheckoutRequest {
        val query = "order=${order.number}&token=${order.accessToken}" + (dropSlug?.let { "&drop=$it" } ?: "")
        return CheckoutRequest(
            orderNumber = order.number,
            email = order.email,
            lines = order.lines.map { PaymentLine(it.productName, it.unitPriceCents, it.quantity, it.imageUrl) },
            shippingLabel = when {
                order.shippingCents == 0L -> "Gratis verzending"
                order.country == "BE" -> "Verzending naar België"
                else -> "Verzending naar Nederland"
            },
            shippingCents = order.shippingCents,
            paymentMethods = PaymentMethods.forCountry(order.country),
            successUrl = "${properties.baseUrl}/bestelling/bedankt?$query",
            cancelUrl = "${properties.baseUrl}/bestelling/geannuleerd?$query",
            // Stripe's shortest: 30 minutes, plus a minute so a slow clock doesn't make it too short.
            expiresAt = clock.instant().plus(STRIPE_MINIMUM_SESSION),
        )
    }

    private companion object {
        val STRIPE_MINIMUM_SESSION: Duration = Duration.ofMinutes(31)
    }
}
