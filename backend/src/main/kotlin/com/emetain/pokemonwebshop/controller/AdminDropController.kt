package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminDropDto
import com.emetain.pokemonwebshop.dto.AdminDropRowDto
import com.emetain.pokemonwebshop.dto.DropLiveDto
import com.emetain.pokemonwebshop.dto.DropProductRequest
import com.emetain.pokemonwebshop.dto.DropQueueRequest
import com.emetain.pokemonwebshop.dto.DropRequest
import com.emetain.pokemonwebshop.orchestration.AdminDropOrchestrator
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

@RestController
@RequestMapping("/v1/admin/drops")
@AdminOnly
class AdminDropController(
    private val dropOrchestrator: AdminDropOrchestrator,
) {

    @GetMapping
    fun list(): List<AdminDropRowDto> = dropOrchestrator.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: DropRequest): AdminDropDto = dropOrchestrator.create(request)

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): AdminDropDto = dropOrchestrator.get(id)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: DropRequest): AdminDropDto = dropOrchestrator.update(id, request)

    /** Only a draft. Its allocated stock goes back. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long, @CurrentUser admin: User) = dropOrchestrator.delete(id, admin)

    @PostMapping("/{id}/publish")
    fun publish(@PathVariable id: Long): AdminDropDto = dropOrchestrator.publish(id)

    @PostMapping("/{id}/unpublish")
    fun unpublish(@PathVariable id: Long): AdminDropDto = dropOrchestrator.unpublish(id)

    @PostMapping("/{id}/products")
    @ResponseStatus(HttpStatus.CREATED)
    fun addProduct(@PathVariable id: Long, @Valid @RequestBody request: DropProductRequest, @CurrentUser admin: User): AdminDropDto =
        dropOrchestrator.addProduct(id, request, admin)

    @PutMapping("/{id}/products/{dropProductId}")
    fun updateProduct(
        @PathVariable id: Long,
        @PathVariable dropProductId: Long,
        @Valid @RequestBody request: DropProductRequest,
        @CurrentUser admin: User,
    ): AdminDropDto = dropOrchestrator.updateProduct(id, dropProductId, request, admin)

    @DeleteMapping("/{id}/products/{dropProductId}")
    fun removeProduct(@PathVariable id: Long, @PathVariable dropProductId: Long, @CurrentUser admin: User): AdminDropDto =
        dropOrchestrator.removeProduct(id, dropProductId, admin)

    @PostMapping("/{id}/banner")
    fun replaceBanner(@PathVariable id: Long, @RequestParam file: MultipartFile): AdminDropDto =
        dropOrchestrator.replaceBanner(id, file.bytes)

    @DeleteMapping("/{id}/banner")
    fun removeBanner(@PathVariable id: Long): AdminDropDto = dropOrchestrator.removeBanner(id)

    /** The live view during the drop, and the report afterwards. */
    @GetMapping("/{id}/live")
    fun live(@PathVariable id: Long): DropLiveDto = dropOrchestrator.live(id)

    /** Pause or resume the waiting room, or change how many get through per minute. */
    @PostMapping("/{id}/queue")
    fun steerQueue(@PathVariable id: Long, @RequestBody request: DropQueueRequest): DropLiveDto = dropOrchestrator.steerQueue(id, request)

    @PostMapping("/{id}/end")
    fun end(@PathVariable id: Long, @CurrentUser admin: User): DropLiveDto = dropOrchestrator.endEarly(id, admin)

    @PostMapping("/{id}/leftover")
    fun returnLeftover(@PathVariable id: Long, @CurrentUser admin: User): DropLiveDto = dropOrchestrator.returnLeftover(id, admin)
}
