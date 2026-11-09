package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.Carrier
import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderLine
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.ShipmentPreference
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page
import java.time.Instant
import java.time.LocalDate

data class OrderLineDto(
    val productId: Long,
    val slug: String,
    val name: String,
    val type: String,
    val imageUrl: String?,
    val imageIsCard: Boolean,
    val unitPriceCents: Long,
    val quantity: Int,
    val lineTotalCents: Long,
    val preOrder: Boolean,
    val shipsFrom: LocalDate?,
) {
    companion object {
        fun from(line: OrderLine) = OrderLineDto(
            productId = line.product.id,
            slug = line.productSlug,
            name = line.productName,
            type = line.productType.name,
            imageUrl = line.imageUrl,
            imageIsCard = line.imageIsCard,
            unitPriceCents = line.unitPriceCents,
            quantity = line.quantity,
            lineTotalCents = line.lineTotalCents,
            preOrder = line.preOrder,
            shipsFrom = line.shipsFrom,
        )
    }
}

data class OrderAddressDto(
    val name: String,
    val street: String,
    val houseNumber: String,
    val postcode: String,
    val city: String,
    val country: String,
)

data class OrderStepDto(val status: OrderStatus, val at: Instant)

/** An order as the customer sees it. */
data class OrderDto(
    val number: String,
    val status: OrderStatus,
    val createdAt: Instant,
    val paidAt: Instant?,
    val shippedAt: Instant?,
    val deliveredAt: Instant?,
    val cancelledAt: Instant?,
    val email: String,
    val address: OrderAddressDto,
    val lines: List<OrderLineDto>,
    val subtotalCents: Long,
    val shippingCents: Long,
    val totalCents: Long,
    val vatCents: Long,
    val refundedCents: Long,
    val deliveryTime: String?,
    val shipmentPreference: ShipmentPreference,
    val hasPreOrders: Boolean,
    val hasInStock: Boolean,
    /** When the last pre-order is expected: an order shipped together waits for it. */
    val expectedShipDate: LocalDate?,
    val paymentMethod: String?,
    val carrier: Carrier?,
    val trackingCode: String?,
    val trackingUrl: String?,
    /** The statuses the order went through, oldest first. */
    val timeline: List<OrderStepDto>,
    val guest: Boolean,
    /** A guest who just paid can make an account with the email of the order. */
    val canCreateAccount: Boolean,
) {
    companion object {
        /** Call this inside a transaction: it reads the lines and the history. */
        fun from(order: Order, canCreateAccount: Boolean = false): OrderDto {
            val steps = order.history
                .fold(mutableListOf<OrderStepDto>()) { steps, change ->
                    if (steps.lastOrNull()?.status != change.status) steps += OrderStepDto(change.status, change.createdAt)
                    steps
                }
            return OrderDto(
                number = order.number,
                status = order.status,
                createdAt = order.createdAt,
                paidAt = order.paidAt,
                shippedAt = order.shippedAt,
                deliveredAt = order.deliveredAt,
                cancelledAt = order.cancelledAt,
                email = order.email,
                address = OrderAddressDto(order.customerName, order.street, order.houseNumber, order.postcode, order.city, order.country),
                lines = order.lines.map(OrderLineDto::from),
                subtotalCents = order.subtotalCents,
                shippingCents = order.shippingCents,
                totalCents = order.totalCents,
                vatCents = order.vatCents,
                refundedCents = order.refundedCents,
                deliveryTime = order.deliveryTime,
                shipmentPreference = order.shipmentPreference,
                hasPreOrders = order.hasPreOrders,
                hasInStock = order.hasInStock,
                expectedShipDate = order.lines.filter { it.preOrder }.mapNotNull { it.shipsFrom }.maxOrNull(),
                paymentMethod = order.paymentMethod,
                carrier = order.carrier,
                trackingCode = order.trackingCode,
                trackingUrl = order.trackingUrl,
                timeline = steps,
                guest = order.user == null,
                canCreateAccount = canCreateAccount,
            )
        }
    }
}

data class OrderSummaryDto(
    val number: String,
    val status: OrderStatus,
    val createdAt: Instant,
    val totalCents: Long,
    val itemCount: Int,
    val imageUrl: String?,
    val imageIsCard: Boolean,
) {
    companion object {
        fun from(order: Order): OrderSummaryDto {
            val first = order.lines.firstOrNull()
            return OrderSummaryDto(
                number = order.number,
                status = order.status,
                createdAt = order.createdAt,
                totalCents = order.totalCents,
                itemCount = order.lines.sumOf { it.quantity },
                imageUrl = first?.imageUrl,
                imageIsCard = first?.imageIsCard ?: false,
            )
        }
    }
}

data class OrderPageDto(
    val items: List<OrderSummaryDto>,
    val page: Int,
    val totalPages: Int,
    val totalItems: Long,
) {
    companion object {
        fun from(page: Page<Order>) = OrderPageDto(
            items = page.content.map(OrderSummaryDto::from),
            page = page.number,
            totalPages = page.totalPages,
            totalItems = page.totalElements,
        )
    }
}

data class OrderTokenRequest(
    @field:NotBlank(message = "Deze link klopt niet.")
    val token: String = "",
)

data class GuestAccountRequest(
    @field:NotBlank(message = "Deze link klopt niet.")
    val token: String = "",

    @field:Size(min = 8, max = 72, message = "Kies een wachtwoord van 8 tot 72 tekens.")
    val password: String = "",
)
