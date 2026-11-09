package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminOrderDto
import com.emetain.pokemonwebshop.dto.AdminOrderListRequest
import com.emetain.pokemonwebshop.dto.AdminOrderPageDto
import com.emetain.pokemonwebshop.dto.CancelOrderRequest
import com.emetain.pokemonwebshop.dto.DashboardDto
import com.emetain.pokemonwebshop.dto.OrderStatusRequest
import com.emetain.pokemonwebshop.dto.RefundRequest
import com.emetain.pokemonwebshop.dto.ShipmentRequest
import com.emetain.pokemonwebshop.dto.ShippingRateDto
import com.emetain.pokemonwebshop.dto.ShippingRateRequest
import com.emetain.pokemonwebshop.orchestration.AdminOrderOrchestrator
import com.emetain.pokemonwebshop.security.AdminOnly
import com.emetain.pokemonwebshop.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/admin")
@AdminOnly
class AdminOrderController(
    private val adminOrderOrchestrator: AdminOrderOrchestrator,
) {

    @GetMapping("/orders")
    fun list(request: AdminOrderListRequest): AdminOrderPageDto = adminOrderOrchestrator.list(request)

    @GetMapping("/orders/{number}")
    fun get(@PathVariable number: String): AdminOrderDto = adminOrderOrchestrator.get(number)

    @PostMapping("/orders/{number}/status")
    fun setStatus(@CurrentUser admin: User, @PathVariable number: String, @Valid @RequestBody body: OrderStatusRequest): AdminOrderDto =
        adminOrderOrchestrator.setStatus(number, body, admin)

    /** Adds the track & trace code and mails the customer. */
    @PostMapping("/orders/{number}/shipment")
    fun ship(@CurrentUser admin: User, @PathVariable number: String, @Valid @RequestBody body: ShipmentRequest): AdminOrderDto =
        adminOrderOrchestrator.ship(number, body, admin)

    @PostMapping("/orders/{number}/cancel")
    fun cancel(@CurrentUser admin: User, @PathVariable number: String, @Valid @RequestBody body: CancelOrderRequest): AdminOrderDto =
        adminOrderOrchestrator.cancel(number, body, admin)

    @PostMapping("/orders/{number}/refund")
    fun refund(@CurrentUser admin: User, @PathVariable number: String, @Valid @RequestBody body: RefundRequest): AdminOrderDto =
        adminOrderOrchestrator.refund(number, body, admin)

    @GetMapping("/shipping-rates")
    fun shippingRates(): List<ShippingRateDto> = adminOrderOrchestrator.shippingRates()

    @PutMapping("/shipping-rates/{country}")
    fun updateShippingRate(@PathVariable country: String, @Valid @RequestBody body: ShippingRateRequest): ShippingRateDto =
        adminOrderOrchestrator.updateShippingRate(country, body)

    @GetMapping("/dashboard")
    fun dashboard(): DashboardDto = adminOrderOrchestrator.dashboard()
}
