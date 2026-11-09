package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.Carrier
import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.OrderStatusChange
import com.emetain.pokemonwebshop.domain.OrderStockState
import com.emetain.pokemonwebshop.service.OrderRules
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page
import java.time.Instant

data class AdminOrderListRequest(
    val q: String? = null,
    /** An [OrderStatus], or `ABANDONED` for checkouts that were never paid. Empty: everything except those. */
    val status: String? = null,
    val page: Int = 0,
    val size: Int? = null,
)

data class AdminOrderRowDto(
    val number: String,
    val status: OrderStatus,
    val createdAt: Instant,
    val paidAt: Instant?,
    val customerName: String,
    val email: String,
    val guest: Boolean,
    val country: String,
    val totalCents: Long,
    val refundedCents: Long,
    val itemCount: Int,
    val hasPreOrders: Boolean,
    val paymentMethod: String?,
) {
    companion object {
        fun from(order: Order) = AdminOrderRowDto(
            number = order.number,
            status = order.status,
            createdAt = order.createdAt,
            paidAt = order.paidAt,
            customerName = order.customerName,
            email = order.email,
            guest = order.user == null,
            country = order.country,
            totalCents = order.totalCents,
            refundedCents = order.refundedCents,
            itemCount = order.lines.sumOf { it.quantity },
            hasPreOrders = order.hasPreOrders,
            paymentMethod = order.paymentMethod,
        )
    }
}

data class AdminOrderPageDto(
    val items: List<AdminOrderRowDto>,
    val page: Int,
    val totalPages: Int,
    val totalItems: Long,
    val size: Int,
) {
    companion object {
        fun from(page: Page<Order>) = AdminOrderPageDto(
            items = page.content.map(AdminOrderRowDto::from),
            page = page.number,
            totalPages = page.totalPages,
            totalItems = page.totalElements,
            size = page.size,
        )
    }
}

data class AdminOrderEventDto(
    val status: OrderStatus,
    val at: Instant,
    val note: String?,
    val adminName: String?,
) {
    companion object {
        fun from(change: OrderStatusChange) =
            AdminOrderEventDto(change.status, change.createdAt, change.note, change.adminUser?.displayName)
    }
}

data class AdminOrderActionsDto(
    /** The statuses the admin can pick by hand. */
    val statuses: List<OrderStatus>,
    val canShip: Boolean,
    val canCancel: Boolean,
    val canRefund: Boolean,
    val refundableCents: Long,
    /** Stock can go back once, after a sale. */
    val canRestock: Boolean,
)

data class AdminOrderDto(
    val order: OrderDto,
    val customerName: String,
    val userId: Long?,
    val history: List<AdminOrderEventDto>,
    val actions: AdminOrderActionsDto,
    val stockState: OrderStockState,
    val reservedUntil: Instant?,
    val stripeSessionId: String?,
    val stripePaymentIntentId: String?,
    val termsAcceptedAt: Instant,
) {
    companion object {
        fun from(order: Order) = AdminOrderDto(
            order = OrderDto.from(order),
            customerName = order.customerName,
            userId = order.user?.id,
            history = order.history.map(AdminOrderEventDto::from),
            actions = AdminOrderActionsDto(
                statuses = OrderRules.manualTargets(order.status).sorted(),
                canShip = OrderRules.canShip(order.status),
                canCancel = OrderRules.canCancel(order.status),
                canRefund = OrderRules.canRefund(order),
                refundableCents = order.refundableCents,
                canRestock = order.stockState == OrderStockState.SOLD,
            ),
            stockState = order.stockState,
            reservedUntil = order.reservedUntil,
            stripeSessionId = order.stripeSessionId,
            stripePaymentIntentId = order.stripePaymentIntentId,
            termsAcceptedAt = order.termsAcceptedAt,
        )
    }
}

data class OrderStatusRequest(
    val status: OrderStatus = OrderStatus.PROCESSING,

    @field:Size(max = 300, message = "De notitie mag maximaal 300 tekens lang zijn.")
    val note: String? = null,
)

data class ShipmentRequest(
    val carrier: Carrier = Carrier.POSTNL,

    @field:NotBlank(message = "Vul de track & trace-code in.")
    @field:Size(max = 100, message = "De code mag maximaal 100 tekens lang zijn.")
    val code: String = "",

    /** Needed for carriers without a link template. Only https. */
    @field:Size(max = 500, message = "De link mag maximaal 500 tekens lang zijn.")
    val url: String? = null,
)

data class CancelOrderRequest(
    @field:Size(max = 300, message = "De reden mag maximaal 300 tekens lang zijn.")
    val note: String? = null,
)

data class RefundRequest(
    /** Empty: everything that is left. */
    val amountCents: Long? = null,
    /** Puts the products back on the stock, for a return that can be sold again. */
    val restock: Boolean = false,

    @field:Size(max = 300, message = "De reden mag maximaal 300 tekens lang zijn.")
    val note: String? = null,
)

data class RevenueDto(val orders: Long, val revenueCents: Long)

data class DashboardDto(
    val today: RevenueDto,
    val week: RevenueDto,
    val month: RevenueDto,
    val openOrders: Long,
    val lowStock: List<AdminProductRowDto>,
    val latestOrders: List<AdminOrderRowDto>,
    val paymentsEnabled: Boolean,
)
