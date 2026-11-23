package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.PremiumGrantRequest
import com.emetain.pokemonwebshop.dto.PremiumMemberDto
import com.emetain.pokemonwebshop.service.PremiumReason
import com.emetain.pokemonwebshop.service.PremiumService
import org.springframework.stereotype.Component
import java.time.ZoneId

/** Premium from admin (2026-11-21): who has it, giving it to a friend, and taking a gift back. */
@Component
class AdminPremiumOrchestrator(
    private val premiumService: PremiumService,
) {

    fun members(): List<PremiumMemberDto> = premiumService.members().map { (user, reason) -> member(user, reason) }

    fun grant(request: PremiumGrantRequest, admin: User): PremiumMemberDto {
        premiumService.grant(request.email, request.lastDay, admin)
        return members().first { it.email == request.email.trim().lowercase() }
    }

    fun revoke(userId: Long, admin: User) {
        premiumService.revoke(userId, admin)
    }

    private fun member(user: User, reason: PremiumReason) = PremiumMemberDto(
        id = user.id,
        email = user.email,
        displayName = user.displayName,
        reason = reason,
        since = user.premiumSince,
        // Premium runs until the start of the day after the last one.
        lastDay = user.premiumUntil.takeIf { reason == PremiumReason.GRANT || reason == PremiumReason.SUBSCRIPTION }
            ?.minusMillis(1)?.atZone(SHOP_ZONE)?.toLocalDate(),
        canRevoke = reason == PremiumReason.FOREVER || reason == PremiumReason.GRANT,
    )

    private companion object {
        val SHOP_ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
    }
}
