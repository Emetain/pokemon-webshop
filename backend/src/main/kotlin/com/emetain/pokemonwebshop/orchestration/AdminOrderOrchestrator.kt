package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminOrderDto
import com.emetain.pokemonwebshop.dto.AdminOrderListRequest
import com.emetain.pokemonwebshop.dto.AdminOrderPageDto
import com.emetain.pokemonwebshop.dto.AdminOrderRowDto
import com.emetain.pokemonwebshop.dto.CancelOrderRequest
import com.emetain.pokemonwebshop.dto.DashboardDto
import com.emetain.pokemonwebshop.dto.OrderStatusRequest
import com.emetain.pokemonwebshop.dto.RefundRequest
import com.emetain.pokemonwebshop.dto.RevenueDto
import com.emetain.pokemonwebshop.dto.ShipmentRequest
import com.emetain.pokemonwebshop.dto.ShippingRateDto
import com.emetain.pokemonwebshop.dto.ShippingRateRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.emetain.pokemonwebshop.repository.OrderSpecifications
import com.emetain.pokemonwebshop.service.OrderNumbers
import com.emetain.pokemonwebshop.service.OrderService
import com.emetain.pokemonwebshop.service.ShippingRateService
import com.emetain.pokemonwebshop.service.payment.PaymentGateway
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Component
class AdminOrderOrchestrator(
    private val orderRepository: OrderRepository,
    private val orderService: OrderService,
    private val shippingRateService: ShippingRateService,
    private val adminProductOrchestrator: AdminProductOrchestrator,
    private val gateway: PaymentGateway,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun list(request: AdminOrderListRequest): AdminOrderPageDto {
        val statusFilter = when (val status = request.status.filterValue()) {
            null -> OrderSpecifications.notAbandoned()
            ABANDONED -> OrderSpecifications.abandoned()
            else -> OrderSpecifications.withStatus(enumFilter<OrderStatus>(status, "Deze status kennen we niet.")!!)
        }
        val filters = listOfNotNull(statusFilter, OrderSpecifications.search(request.q))
        val size = request.size?.takeIf { it in PAGE_SIZES } ?: PAGE_SIZES.first()
        val page = PageRequest.of(request.page.coerceAtLeast(0), size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        return AdminOrderPageDto.from(orderRepository.findAll(Specification.allOf(filters), page))
    }

    fun get(number: String): AdminOrderDto = transactions.execute { AdminOrderDto.from(find(number)) }!!

    fun setStatus(number: String, request: OrderStatusRequest, admin: User): AdminOrderDto {
        orderService.setStatus(id(number), request.status, request.note, admin)
        return get(number)
    }

    fun ship(number: String, request: ShipmentRequest, admin: User): AdminOrderDto {
        orderService.ship(id(number), request.carrier, request.code, request.url, admin)
        return get(number)
    }

    fun cancel(number: String, request: CancelOrderRequest, admin: User): AdminOrderDto {
        retryingOnStockConflict { orderService.cancel(id(number), request.note, admin) }
        return get(number)
    }

    fun refund(number: String, request: RefundRequest, admin: User): AdminOrderDto {
        retryingOnStockConflict { orderService.refund(id(number), request.amountCents, request.restock, request.note, admin) }
        return get(number)
    }

    fun shippingRates(): List<ShippingRateDto> = shippingRateService.all().map(ShippingRateDto::from)

    fun updateShippingRate(country: String, request: ShippingRateRequest): ShippingRateDto =
        ShippingRateDto.from(shippingRateService.update(country.uppercase(), request))

    /** Revenue is what was paid minus what went back, by the day of payment in Dutch time. */
    @Transactional(readOnly = true)
    fun dashboard(): DashboardDto {
        val today = clock.instant().atZone(DUTCH_TIME).toLocalDate()
        fun revenueSince(day: java.time.LocalDate): RevenueDto {
            val row = orderRepository.revenueSince(day.atStartOfDay(DUTCH_TIME).toInstant())
            return RevenueDto(row.orders, row.totalCents - row.refundedCents)
        }
        val latest = orderRepository.findAll(
            OrderSpecifications.notAbandoned(),
            PageRequest.of(0, LATEST, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
        )
        return DashboardDto(
            today = revenueSince(today),
            week = revenueSince(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))),
            month = revenueSince(today.withDayOfMonth(1)),
            openOrders = orderRepository.countByStatusIn(OrderStatus.OPEN),
            lowStock = adminProductOrchestrator.lowStock().take(LOW_STOCK),
            latestOrders = latest.content.map(AdminOrderRowDto::from),
            paymentsEnabled = gateway.configured,
        )
    }

    private fun find(number: String): Order =
        orderRepository.findByNumber(OrderNumbers.normalize(number)) ?: throw ApiException.notFound("We konden deze bestelling niet vinden.")

    private fun id(number: String): Long = find(number).id

    private companion object {
        const val ABANDONED = "ABANDONED"
        val PAGE_SIZES = listOf(25, 50, 100)
        const val LATEST = 8
        const val LOW_STOCK = 8
        val DUTCH_TIME: ZoneId = ZoneId.of("Europe/Amsterdam")
    }
}
