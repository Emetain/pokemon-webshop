package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminHeroSlideDto
import com.emetain.pokemonwebshop.dto.AdminSetReleaseDto
import com.emetain.pokemonwebshop.dto.ProductSummaryDto
import com.emetain.pokemonwebshop.dto.ReleaseCalendarDto
import com.emetain.pokemonwebshop.dto.SetReleaseRequest
import com.emetain.pokemonwebshop.orchestration.ReleaseOrchestrator
import com.emetain.pokemonwebshop.security.AdminOnly
import com.emetain.pokemonwebshop.security.CurrentUser
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

/** The release calendar and every pre-order in the shop, by release date. */
@RestController
@RequestMapping("/v1")
class ReleaseController(
    private val releaseOrchestrator: ReleaseOrchestrator,
) {

    @GetMapping("/releases")
    fun calendar(@RequestParam(required = false) game: String?, @CurrentUser viewer: User?): ReleaseCalendarDto =
        releaseOrchestrator.calendar(game, viewer)

    @GetMapping("/pre-orders")
    fun preOrders(@RequestParam(required = false) game: String?, @CurrentUser viewer: User?): List<ProductSummaryDto> =
        releaseOrchestrator.preOrders(game, viewer)
}

@RestController
@RequestMapping("/v1/admin/releases")
@AdminOnly
class AdminReleaseController(
    private val releaseOrchestrator: ReleaseOrchestrator,
) {

    @GetMapping
    fun all(): List<AdminSetReleaseDto> = releaseOrchestrator.all()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: SetReleaseRequest): AdminSetReleaseDto = releaseOrchestrator.create(request)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: SetReleaseRequest): AdminSetReleaseDto =
        releaseOrchestrator.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = releaseOrchestrator.delete(id)

    @PostMapping("/{id}/image")
    fun replaceImage(@PathVariable id: Long, @RequestParam file: MultipartFile): AdminSetReleaseDto =
        releaseOrchestrator.replaceImage(id, file.bytes)

    @DeleteMapping("/{id}/image")
    fun removeImage(@PathVariable id: Long): AdminSetReleaseDto = releaseOrchestrator.removeImage(id)

    /** A slide in the home page hero for this release, in one click. */
    @PostMapping("/{id}/hero-slide")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSlide(@PathVariable id: Long): AdminHeroSlideDto = releaseOrchestrator.createSlide(id)
}
