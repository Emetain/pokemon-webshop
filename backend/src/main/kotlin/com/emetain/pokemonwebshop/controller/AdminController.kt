package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminStatusDto
import com.emetain.pokemonwebshop.orchestration.AdminOrchestrator
import com.emetain.pokemonwebshop.security.AdminOnly
import com.emetain.pokemonwebshop.security.CurrentUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/admin")
@AdminOnly
class AdminController(
    private val adminOrchestrator: AdminOrchestrator,
) {

    @GetMapping("/status")
    fun status(@CurrentUser user: User): AdminStatusDto =
        adminOrchestrator.status(user)
}
