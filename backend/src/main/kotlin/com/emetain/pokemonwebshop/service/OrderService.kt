package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Carrier
import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.OrderStatusChange
import com.emetain.pokemonwebshop.domain.OrderStockState
import com.emetain.pokemonwebshop.domain.StockReason
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.emetain.pokemonwebshop.service.payment.PaymentGateway
import com.emetain.pokemonwebshop.service.payment.PaymentUnavailableException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.util.Locale

/** What the admin does with an order after it is paid: process, ship, cancel, refund. */
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderPaymentService: OrderPaymentService,
    private val stockService: StockService,
    private val gateway: PaymentGateway,
    private val events: ApplicationEventPublisher,
    private val properties: AppProperties,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {

    @Transactional
    fun setStatus(orderId: Long, target: OrderStatus, note: String?, admin: User): Order {
        val order = locked(orderId)
        if (target !in OrderRules.manualTargets(order.status)) {
            throw ApiException.validation("Een bestelling met deze status kan niet naar ${statusText(target)}.")
        }
        val now = clock.instant()
        order.status = target
        if (target == OrderStatus.DELIVERED) order.deliveredAt = now
        if (target == OrderStatus.SHIPPED) order.deliveredAt = null
        order.updatedAt = now
        order.history += OrderStatusChange(order, target, now, note?.trim()?.takeIf { it.isNotEmpty() }, admin)
        return order
    }

    /** Adds or corrects the track & trace code, and mails the customer each time. */
    @Transactional
    fun ship(orderId: Long, carrier: Carrier, code: String, url: String?, admin: User): Order {
        val order = locked(orderId)
        if (!OrderRules.canShip(order.status)) throw ApiException.validation("Deze bestelling kan niet verzonden worden.")
        val cleanCode = code.trim()
        val link = url?.trim()?.takeIf { it.isNotEmpty() }?.also {
            if (!TrackingLinks.isSafe(it)) throw ApiException.validation("Gebruik een link die met https:// begint.")
        } ?: TrackingLinks.build(properties.checkout.trackingUrls[carrier], cleanCode, order.postcode, order.country)
        val now = clock.instant()
        val correction = order.status == OrderStatus.SHIPPED
        order.carrier = carrier
        order.trackingCode = cleanCode
        order.trackingUrl = link
        order.status = OrderStatus.SHIPPED
        order.shippedAt = order.shippedAt ?: now
        order.updatedAt = now
        val text = "${if (correction) "Track & trace aangepast" else "Verzonden"}: ${TrackingLinks.carrierLabel(carrier)} $cleanCode"
        order.history += OrderStatusChange(order, OrderStatus.SHIPPED, now, text, admin)
        events.publishEvent(OrderMailEvent(order.id, OrderMail.SHIPPED))
        return order
    }

    /**
     * Cancels before shipping. An unpaid order lets go of its stock. A paid one is paid back in full first, and its
     * products go back on the stock.
     */
    fun cancel(orderId: Long, note: String?, admin: User): Order {
        val order = orderRepository.findById(orderId).orElseThrow { notFound() }
        if (!OrderRules.canCancel(order.status)) throw ApiException.validation("Deze bestelling kan niet meer geannuleerd worden.")
        val reason = note?.trim()?.takeIf { it.isNotEmpty() } ?: "Geannuleerd door de winkel"
        if (order.status == OrderStatus.PENDING_PAYMENT) {
            if (!orderPaymentService.closePending(orderId, reason, admin)) {
                throw ApiException(HttpStatus.CONFLICT, "order_changed", "De betaling van deze bestelling loopt nog of is net binnengekomen. Kijk over een minuut opnieuw.")
            }
            return order
        }
        val amount = order.refundableCents
        val refunded = payBack(order, amount, "cancel-${order.number}")
        return transactions.execute { recordCancel(orderId, refunded, reason, admin) }!!
    }

    /** Pays back part or all of a paid order, for example after a return. */
    fun refund(orderId: Long, amountCents: Long?, restock: Boolean, note: String?, admin: User?): Order {
        val order = orderRepository.findById(orderId).orElseThrow { notFound() }
        if (!OrderRules.canRefund(order)) throw ApiException.validation("Er valt bij deze bestelling niets terug te betalen.")
        val amount = amountCents ?: order.refundableCents
        if (amount <= 0 || amount > order.refundableCents) {
            throw ApiException.validation("Kies een bedrag tot ${Money.format(order.refundableCents)}.")
        }
        val refunded = payBack(order, amount, "refund-${order.number}-${order.refundedCents}-$amount")
        return transactions.execute { recordRefund(orderId, refunded, restock, note, admin) }!!
    }

    /** Inside a transaction: Stripe has paid back already. */
    private fun recordCancel(orderId: Long, refundedCents: Long, reason: String, admin: User): Order {
        val order = locked(orderId)
        val now = clock.instant()
        if (order.status !in OrderStatus.OPEN) return order
        returnStock(order, "Bestelling ${order.number} geannuleerd", admin)
        order.refundedCents += refundedCents
        order.status = OrderStatus.CANCELLED
        order.cancelledAt = now
        order.updatedAt = now
        val refundText = if (refundedCents > 0) " ${Money.format(refundedCents)} teruggestort." else " Niets teruggestort: er is geen betaling bij Stripe."
        order.history += OrderStatusChange(order, OrderStatus.CANCELLED, now, "$reason.$refundText".replace("..", "."), admin)
        events.publishEvent(OrderMailEvent(order.id, OrderMail.CANCELLED, refundedCents))
        return order
    }

    private fun recordRefund(orderId: Long, refundedCents: Long, restock: Boolean, note: String?, admin: User?): Order {
        val order = locked(orderId)
        val now = clock.instant()
        order.refundedCents += refundedCents
        if (restock) returnStock(order, "Retour van bestelling ${order.number}", admin)
        if (order.refundedCents >= order.totalCents && order.status in setOf(OrderStatus.SHIPPED, OrderStatus.DELIVERED)) {
            order.status = OrderStatus.REFUNDED
        }
        order.updatedAt = now
        val text = listOfNotNull(
            "${Money.format(refundedCents)} teruggestort",
            if (restock) "voorraad teruggeboekt" else null,
            note?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(". ")
        order.history += OrderStatusChange(order, order.status, now, text, admin)
        events.publishEvent(OrderMailEvent(order.id, OrderMail.REFUNDED, refundedCents))
        return order
    }

    /** Puts a guest order in the account that was just made for it. */
    @Transactional
    fun linkToUser(orderId: Long, user: User) {
        orderRepository.findLockedById(orderId)?.takeIf { it.user == null }?.user = user
    }

    /** After a payment that came too late. The admin can try again from the order should this fail. */
    fun refundAutomatically(orderId: Long) {
        refund(orderId, null, restock = false, note = "Automatisch: betaald na het verlopen van de reservering", admin = null)
    }

    /** Pays back at Stripe before anything changes here, so a failed refund leaves the order as it was. */
    private fun payBack(order: Order, amountCents: Long, idempotencyKey: String): Long {
        val paymentIntent = order.stripePaymentIntentId ?: return 0
        if (amountCents <= 0) return 0
        try {
            gateway.refund(paymentIntent, amountCents, idempotencyKey, order.number)
        } catch (exception: PaymentUnavailableException) {
            throw ApiException(HttpStatus.BAD_GATEWAY, "refund_failed", "Stripe kon het bedrag niet terugstorten. Probeer het opnieuw of kijk in het Stripe-dashboard.")
        }
        return amountCents
    }

    private fun returnStock(order: Order, note: String, admin: User?) {
        if (order.stockState != OrderStockState.SOLD) return
        for (line in order.lines) stockService.adjust(line.product.id, line.quantity, StockReason.RETURN, note, admin)
        order.stockState = OrderStockState.RETURNED
    }

    private fun locked(orderId: Long): Order = orderRepository.findLockedById(orderId) ?: throw notFound()

    private fun notFound() = ApiException.notFound("We konden deze bestelling niet vinden.")

    private fun statusText(status: OrderStatus) = status.name.lowercase(Locale.ROOT).replace('_', ' ')

    companion object {
        /** A guest can make an account from the success page for this long after paying. */
        val GUEST_ACCOUNT_WINDOW: Duration = Duration.ofDays(1)
    }
}

/** Euro amounts in Dutch notation, for notes and mails. */
object Money {
    private val format = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("nl-NL")).apply {
        currency = java.util.Currency.getInstance("EUR")
    }

    @Synchronized
    fun format(cents: Long): String = format.format(java.math.BigDecimal.valueOf(cents, 2)).replace('\u00a0', ' ')
}
