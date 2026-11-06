package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.dto.CatalogImageDto
import com.emetain.pokemonwebshop.orchestration.AdminCatalogImageOrchestrator
import com.emetain.pokemonwebshop.security.AdminOnly
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/v1/admin/catalog/images")
@AdminOnly
class AdminCatalogImageController(
    private val imageOrchestrator: AdminCatalogImageOrchestrator,
) {

    @PostMapping("/cards/{id}")
    fun replaceCardImage(@PathVariable id: Long, @RequestParam file: MultipartFile): CatalogImageDto =
        imageOrchestrator.replaceCardImage(id, file.bytes)

    @DeleteMapping("/cards/{id}")
    fun resetCardImage(@PathVariable id: Long): CatalogImageDto =
        imageOrchestrator.resetCardImage(id)

    @PostMapping("/sets/{game}/{code}")
    fun replaceSetLogo(
        @PathVariable game: String,
        @PathVariable code: String,
        @RequestParam file: MultipartFile,
    ): CatalogImageDto = imageOrchestrator.replaceSetLogo(game, code, file.bytes)

    @DeleteMapping("/sets/{game}/{code}")
    fun resetSetLogo(@PathVariable game: String, @PathVariable code: String): CatalogImageDto =
        imageOrchestrator.resetSetLogo(game, code)
}
