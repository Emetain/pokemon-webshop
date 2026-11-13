package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.DiscountCode
import com.emetain.pokemonwebshop.dto.AdminDiscountCodeDto
import com.emetain.pokemonwebshop.dto.DiscountCodeRequest
import com.emetain.pokemonwebshop.service.DiscountCodeService
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class AdminDiscountCodeOrchestrator(
    private val codeService: DiscountCodeService,
    private val clock: Clock,
) {

    fun all(): List<AdminDiscountCodeDto> {
        val usage = codeService.usage()
        val now = clock.instant()
        return codeService.all().map { AdminDiscountCodeDto.from(it, usage[it.id], now) }
    }

    fun create(request: DiscountCodeRequest): AdminDiscountCodeDto = dto(codeService.create(request))

    fun update(codeId: Long, request: DiscountCodeRequest): AdminDiscountCodeDto = dto(codeService.update(codeId, request))

    fun delete(codeId: Long) = codeService.delete(codeId)

    private fun dto(code: DiscountCode) = AdminDiscountCodeDto.from(code, codeService.usage()[code.id], clock.instant())
}
