package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.dto.AdminProductRowDto
import com.emetain.pokemonwebshop.dto.StockMovementPageDto
import com.emetain.pokemonwebshop.orchestration.AdminProductOrchestrator
import com.emetain.pokemonwebshop.security.AdminOnly
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/admin/stock")
@AdminOnly
class AdminStockController(
    private val productOrchestrator: AdminProductOrchestrator,
) {

    @GetMapping("/low")
    fun lowStock(): List<AdminProductRowDto> =
        productOrchestrator.lowStock()

    @GetMapping("/movements")
    fun movements(@RequestParam(defaultValue = "0") page: Int): StockMovementPageDto =
        productOrchestrator.latestMovements(page)
}
