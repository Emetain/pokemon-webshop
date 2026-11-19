package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.ScanResultDto
import com.emetain.pokemonwebshop.dto.ScannerStatusDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.orchestration.ScannerOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RateLimited
import com.emetain.pokemonwebshop.security.RequiresLogin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/** The card scanner (phase 11). Logged in only, so scans can be counted. */
@RestController
@RequestMapping("/v1/scanner")
@RequiresLogin
class ScannerController(
    private val scannerOrchestrator: ScannerOrchestrator,
) {

    @GetMapping
    fun status(@CurrentUser user: User): ScannerStatusDto = scannerOrchestrator.status(user)

    @PostMapping
    @RateLimited("scanner")
    fun scan(
        @CurrentUser user: User,
        @RequestPart("photo") photo: MultipartFile,
        @RequestParam(required = false) game: String?,
    ): ScanResultDto {
        val hint = game?.takeIf { it.isNotBlank() }?.let { Game.fromSlug(it) ?: throw ApiException.validation("Kies een geldig spel.") }
        return scannerOrchestrator.scan(user, photo.bytes, hint)
    }
}
