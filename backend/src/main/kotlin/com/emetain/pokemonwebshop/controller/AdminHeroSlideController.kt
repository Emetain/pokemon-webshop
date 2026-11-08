package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.dto.AdminHeroSlideDto
import com.emetain.pokemonwebshop.dto.HeroSlideRequest
import com.emetain.pokemonwebshop.orchestration.HeroSlideOrchestrator
import com.emetain.pokemonwebshop.security.AdminOnly
import jakarta.validation.Valid
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
@RequestMapping("/v1/admin/hero-slides")
@AdminOnly
class AdminHeroSlideController(
    private val slideOrchestrator: HeroSlideOrchestrator,
) {

    @GetMapping
    fun all(): List<AdminHeroSlideDto> = slideOrchestrator.all()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: HeroSlideRequest): AdminHeroSlideDto =
        slideOrchestrator.create(request)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: HeroSlideRequest): AdminHeroSlideDto =
        slideOrchestrator.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = slideOrchestrator.delete(id)

    /** `direction` is `up` (earlier in the carousel) or `down`. */
    @PostMapping("/{id}/move")
    fun move(@PathVariable id: Long, @RequestParam direction: String): List<AdminHeroSlideDto> =
        slideOrchestrator.move(id, direction == "up")

    @PostMapping("/{id}/image")
    fun replaceImage(@PathVariable id: Long, @RequestParam file: MultipartFile): AdminHeroSlideDto =
        slideOrchestrator.replaceImage(id, file.bytes)

    @DeleteMapping("/{id}/image")
    fun removeImage(@PathVariable id: Long): AdminHeroSlideDto = slideOrchestrator.removeImage(id)
}
