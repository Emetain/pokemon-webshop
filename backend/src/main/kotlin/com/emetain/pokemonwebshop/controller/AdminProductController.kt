package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminProductDto
import com.emetain.pokemonwebshop.dto.AdminProductListRequest
import com.emetain.pokemonwebshop.dto.AdminProductPageDto
import com.emetain.pokemonwebshop.dto.ImageOrderRequest
import com.emetain.pokemonwebshop.dto.MarketSuggestionDto
import com.emetain.pokemonwebshop.dto.ProductRequest
import com.emetain.pokemonwebshop.dto.QuickSingleRequest
import com.emetain.pokemonwebshop.dto.StockAdjustRequest
import com.emetain.pokemonwebshop.dto.StockChangeDto
import com.emetain.pokemonwebshop.dto.StockCountRequest
import com.emetain.pokemonwebshop.dto.StockMovementPageDto
import com.emetain.pokemonwebshop.orchestration.AdminProductOrchestrator
import com.emetain.pokemonwebshop.security.AdminOnly
import com.emetain.pokemonwebshop.security.CurrentUser
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/v1/admin/products")
@AdminOnly
class AdminProductController(
    private val productOrchestrator: AdminProductOrchestrator,
) {

    @GetMapping
    fun list(@ParameterObject request: AdminProductListRequest): AdminProductPageDto =
        productOrchestrator.list(request)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: ProductRequest): AdminProductDto =
        productOrchestrator.create(request)

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): AdminProductDto =
        productOrchestrator.get(id)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: ProductRequest): AdminProductDto =
        productOrchestrator.update(id, request)

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    fun duplicate(@PathVariable id: Long): AdminProductDto =
        productOrchestrator.duplicate(id)

    @PostMapping("/{id}/archive")
    fun archive(@PathVariable id: Long): AdminProductDto =
        productOrchestrator.archive(id)

    @PostMapping("/{id}/images")
    fun addImage(@PathVariable id: Long, @RequestParam file: MultipartFile): AdminProductDto =
        productOrchestrator.addImage(id, file.bytes)

    @DeleteMapping("/{id}/images/{index}")
    fun removeImage(@PathVariable id: Long, @PathVariable index: Int): AdminProductDto =
        productOrchestrator.removeImage(id, index)

    @PutMapping("/{id}/images")
    fun reorderImages(@PathVariable id: Long, @RequestBody request: ImageOrderRequest): AdminProductDto =
        productOrchestrator.reorderImages(id, request.urls)

    @GetMapping("/{id}/stock/movements")
    fun movements(@PathVariable id: Long, @RequestParam(defaultValue = "0") page: Int): StockMovementPageDto =
        productOrchestrator.movements(id, page)

    @PostMapping("/{id}/stock/adjustments")
    fun adjustStock(
        @PathVariable id: Long,
        @Valid @RequestBody request: StockAdjustRequest,
        @CurrentUser admin: User,
    ): StockChangeDto = productOrchestrator.adjustStock(id, request, admin)

    @PostMapping("/{id}/stock/count")
    fun countStock(
        @PathVariable id: Long,
        @Valid @RequestBody request: StockCountRequest,
        @CurrentUser admin: User,
    ): StockChangeDto = productOrchestrator.countStock(id, request, admin)

    @PostMapping("/singles")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSingle(@Valid @RequestBody request: QuickSingleRequest, @CurrentUser admin: User): AdminProductDto =
        productOrchestrator.createSingle(request, admin)

    @GetMapping("/singles/market/{cardId}")
    fun marketSuggestion(@PathVariable cardId: Long): MarketSuggestionDto =
        productOrchestrator.marketSuggestion(cardId)
}
