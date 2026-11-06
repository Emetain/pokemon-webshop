package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.dto.CatalogSyncStatusDto
import com.emetain.pokemonwebshop.dto.SyncRunDto
import com.emetain.pokemonwebshop.orchestration.AdminCatalogOrchestrator
import com.emetain.pokemonwebshop.security.AdminOnly
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/admin/catalog")
@AdminOnly
class AdminCatalogController(
    private val adminCatalogOrchestrator: AdminCatalogOrchestrator,
) {

    @GetMapping("/sync")
    fun syncStatus(): List<CatalogSyncStatusDto> =
        adminCatalogOrchestrator.syncStatus()

    @PostMapping("/sync/{game}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun startSync(@PathVariable game: String): SyncRunDto =
        adminCatalogOrchestrator.startSync(game)
}
