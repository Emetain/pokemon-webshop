package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.PremiumGrantRequest
import com.emetain.pokemonwebshop.dto.PremiumMemberDto
import com.emetain.pokemonwebshop.orchestration.AdminPremiumOrchestrator
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

/** Giving Premium to friends who help, without paying (2026-11-21). */
@RestController
@RequestMapping("/v1/admin/premium")
@AdminOnly
class AdminPremiumController(
    private val orchestrator: AdminPremiumOrchestrator,
) {

    @GetMapping
    fun members(): List<PremiumMemberDto> = orchestrator.members()

    @PostMapping("/grants")
    fun grant(@CurrentUser admin: User, @Valid @RequestBody request: PremiumGrantRequest): PremiumMemberDto =
        orchestrator.grant(request, admin)

    @DeleteMapping("/grants/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(@CurrentUser admin: User, @PathVariable userId: Long) = orchestrator.revoke(userId, admin)
}
