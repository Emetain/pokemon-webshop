package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminMysteryBatchDto
import com.emetain.pokemonwebshop.dto.MysteryBoxBatchRequest
import com.emetain.pokemonwebshop.orchestration.AdminMysteryBoxOrchestrator
import com.emetain.pokemonwebshop.security.AdminOnly
import com.emetain.pokemonwebshop.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The batches of a mystery box product. Each box in a batch is one unit of the product's stock. */
@RestController
@RequestMapping("/v1/admin")
@AdminOnly
class AdminMysteryBoxController(
    private val mysteryBoxOrchestrator: AdminMysteryBoxOrchestrator,
) {

    @GetMapping("/products/{productId}/mystery-batches")
    fun batches(@PathVariable productId: Long): List<AdminMysteryBatchDto> = mysteryBoxOrchestrator.batches(productId)

    @PostMapping("/products/{productId}/mystery-batches")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable productId: Long,
        @Valid @RequestBody request: MysteryBoxBatchRequest,
        @CurrentUser admin: User,
    ): List<AdminMysteryBatchDto> = mysteryBoxOrchestrator.create(productId, request, admin)

    @DeleteMapping("/mystery-batches/{batchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable batchId: Long, @CurrentUser admin: User) = mysteryBoxOrchestrator.delete(batchId, admin)
}
