package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.ProductDetailDto
import com.emetain.pokemonwebshop.dto.ProductListDto
import com.emetain.pokemonwebshop.dto.ProductListRequest
import com.emetain.pokemonwebshop.dto.ProductSummaryDto
import com.emetain.pokemonwebshop.dto.StockAlertRequest
import com.emetain.pokemonwebshop.orchestration.ProductOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RateLimited
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/products")
class ProductController(
    private val productOrchestrator: ProductOrchestrator,
) {

    @GetMapping
    fun list(@ParameterObject request: ProductListRequest, @CurrentUser viewer: User?): ProductListDto =
        productOrchestrator.list(request, viewer)

    @GetMapping("/new-arrivals")
    fun newArrivals(@RequestParam(defaultValue = "12") limit: Int, @CurrentUser viewer: User?): List<ProductSummaryDto> =
        productOrchestrator.newArrivals(limit, viewer)

    @GetMapping("/bestsellers")
    fun bestsellers(@RequestParam(defaultValue = "12") limit: Int, @CurrentUser viewer: User?): List<ProductSummaryDto> =
        productOrchestrator.bestsellers(limit, viewer)

    @GetMapping("/{slug}")
    fun detail(@PathVariable slug: String, @CurrentUser viewer: User?): ProductDetailDto =
        productOrchestrator.detail(slug, viewer)

    @PostMapping("/{slug}/stock-alerts")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RateLimited("stock-alert")
    fun signUpForStock(@PathVariable slug: String, @RequestBody request: StockAlertRequest) =
        productOrchestrator.signUpForStock(slug, request.email)
}
