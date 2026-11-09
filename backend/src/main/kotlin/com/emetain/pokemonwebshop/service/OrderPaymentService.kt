package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.OrderStatusChange
import com.emetain.pokemonwebshop.domain.OrderStockState
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.emetain.pokemonwebshop.service.payment.ExpireResult
import com.emetain.pokemonwebshop.service.payment.PaymentGateway
import com.emetain.pokemonwebshop.service.payment.PaymentUnavailableException
import com.emetain.pokemonwebshop.service.payment.SessionState
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration

/** What a confirmed payment did to an order. */
enum class PaymentOutcome {
    /** The order is paid now. */
    PAID,

    /** It was paid before: a webhook that came twice. Nothing changed. */
    ALREADY_PAID,

    /** Paid after its stock went to someone else: the money has to go back. */
    NEEDS_REFUND,
}

/** The payment side of an order: what the webhook confirms, and closing checkouts that weren't paid. */
@Service
class OrderPaymentService(
    private val orderRepository: OrderRepository,
    private val checkoutService: CheckoutService,
    private val stockService: StockService,
    private val cartService: CartService,
    private val gateway: PaymentGateway,
    private val events: ApplicationEventPublisher,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * The only way an order becomes paid. Called for a session the payment provider confirmed as paid. Safe to call
     * twice, also at the same moment: the lock on the order lets one call convert the stock, the other sees it paid.
     */
    @Transactional
    fun markPaid(orderId: Long, state: SessionState): PaymentOutcome {
        val order = orderRepository.findLockedById(orderId) ?: return PaymentOutcome.ALREADY_PAID
        if (order.paidAt != null) return PaymentOutcome.ALREADY_PAID
        val now = clock.instant()
        order.paidAt = now
        order.updatedAt = now
        order.stripePaymentIntentId = state.paymentIntentId
        order.paymentMethod = state.paymentMethod
        if (state.amountTotalCents != order.totalCents || !state.currency.equals("eur", ignoreCase = true)) {
            log.error(
                "Order {} was paid {} {} but costs {} EUR",
                order.number, state.amountTotalCents, state.currency, order.totalCents,
            )
            order.history += note(order, "Let op: betaald bedrag (${state.amountTotalCents} ${state.currency}) wijkt af van het totaal.")
        }

        val outcome = when (order.stockState) {
            OrderStockState.RESERVED -> {
                for (line in order.lines) stockService.sellReserved(line.product.id, line.quantity, "Bestelling ${order.number}")
                PaymentOutcome.PAID
            }
            // The reservation had run out. Should Stripe still take a payment, the stock may still be there.
            else -> if (order.lines.all { it.product.stock.available >= it.quantity }) {
                for (line in order.lines) stockService.sell(line.product.id, line.quantity, "Bestelling ${order.number}")
                order.history += note(order, "Betaald nadat de reservering was verlopen. De voorraad was er nog.")
                PaymentOutcome.PAID
            } else {
                PaymentOutcome.NEEDS_REFUND
            }
        }

        if (outcome == PaymentOutcome.PAID) {
            order.stockState = OrderStockState.SOLD
            order.reservedUntil = null
            order.status = OrderStatus.PAID
            order.cancelledAt = null
            order.history += OrderStatusChange(order, OrderStatus.PAID, now, "Betaald${methodText(order.paymentMethod)}")
            order.cartId?.let { cartId ->
                cartService.removeOrdered(cartId, order.lines.groupBy { it.product.id }.mapValues { (_, lines) -> lines.sumOf { it.quantity } })
            }
            events.publishEvent(OrderMailEvent(order.id, OrderMail.CONFIRMATION))
        } else {
            order.status = OrderStatus.CANCELLED
            order.cancelledAt = order.cancelledAt ?: now
            order.history += OrderStatusChange(
                order,
                OrderStatus.CANCELLED,
                now,
                "Betaald nadat de reservering was verlopen, en niet meer op voorraad. Het bedrag gaat terug.",
            )
        }
        return outcome
    }

    /**
     * Closes a checkout that wasn't paid: first the payment page, so nobody can still pay, then the stock goes back.
     * When the payment provider can't be reached, the stock stays until the payment page has run out by itself.
     * Returns whether the stock went back.
     */
    fun closePending(orderId: Long, reason: String, admin: User? = null): Boolean {
        val order = orderRepository.findById(orderId).orElse(null) ?: return false
        if (order.status != OrderStatus.PENDING_PAYMENT || order.stockState != OrderStockState.RESERVED) return false
        val sessionId = order.stripeSessionId
        if (sessionId != null) {
            val result = try {
                if (gateway.configured) gateway.expire(sessionId) else null
            } catch (exception: PaymentUnavailableException) {
                log.warn("Could not close the payment page of order {}: {}", order.number, exception.message)
                null
            }
            when (result) {
                ExpireResult.COMPLETE -> {
                    // Paid: the webhook converts the reservation. Look again in a while should it not come.
                    holdLonger(orderId)
                    return false
                }
                ExpireResult.EXPIRED, ExpireResult.NOT_FOUND -> Unit
                null -> if (!expiredByItself(order)) return false
            }
        }
        return checkoutService.releaseReservation(orderId, reason, admin)
    }

    private fun holdLonger(orderId: Long) {
        transactions.executeWithoutResult {
            val order = orderRepository.findLockedById(orderId)
            if (order?.stockState == OrderStockState.RESERVED) order.reservedUntil = clock.instant().plus(PAID_WEBHOOK_WAIT)
        }
    }

    /** Stripe closes its payment page by itself at the time it gave. A minute later, nobody can pay on it. */
    private fun expiredByItself(order: Order): Boolean =
        order.stripeSessionExpiresAt?.let { clock.instant().isAfter(it.plus(Duration.ofMinutes(1))) } ?: true

    private fun note(order: Order, text: String) = OrderStatusChange(order, order.status, clock.instant(), text)

    private fun methodText(method: String?): String = when (method) {
        PaymentMethods.IDEAL -> " met iDEAL"
        PaymentMethods.BANCONTACT -> " met Bancontact"
        PaymentMethods.CARD -> " met een creditcard"
        null -> ""
        else -> " met $method"
    }

    private companion object {
        val PAID_WEBHOOK_WAIT: Duration = Duration.ofMinutes(10)
    }
}
