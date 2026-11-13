package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminMysteryBatchDto
import com.emetain.pokemonwebshop.dto.MysteryBoxBatchRequest
import com.emetain.pokemonwebshop.service.MysteryBoxService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AdminMysteryBoxOrchestrator(
    private val mysteryBoxService: MysteryBoxService,
) {

    @Transactional(readOnly = true)
    fun batches(productId: Long): List<AdminMysteryBatchDto> =
        mysteryBoxService.batches(productId).map { AdminMysteryBatchDto.from(it, mysteryBoxService.draws(it.id)) }

    @Transactional
    fun create(productId: Long, request: MysteryBoxBatchRequest, admin: User): List<AdminMysteryBatchDto> {
        mysteryBoxService.createBatch(productId, request, admin)
        return batches(productId)
    }

    @Transactional
    fun delete(batchId: Long, admin: User) = mysteryBoxService.deleteBatch(batchId, admin)
}
