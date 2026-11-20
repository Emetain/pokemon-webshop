package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.BinderDetailDto
import com.emetain.pokemonwebshop.dto.BinderEntryRequest
import com.emetain.pokemonwebshop.dto.BinderListDto
import com.emetain.pokemonwebshop.dto.BinderQuantityRequest
import com.emetain.pokemonwebshop.dto.BinderRequest
import com.emetain.pokemonwebshop.dto.CardBindersDto
import com.emetain.pokemonwebshop.orchestration.ExtraBinderOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RequiresLogin
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Extra binders next to the collection (Premium, phase 12). Not the home page binder, which is `/v1/binder`. */
@RestController
@RequestMapping("/v1/binders")
@RequiresLogin
class ExtraBinderController(
    private val orchestrator: ExtraBinderOrchestrator,
) {

    @GetMapping
    fun list(@CurrentUser user: User): BinderListDto = orchestrator.list(user)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@CurrentUser user: User, @Valid @RequestBody request: BinderRequest): BinderDetailDto =
        orchestrator.create(user, request.name)

    @GetMapping("/{id}")
    fun detail(@CurrentUser user: User, @PathVariable id: Long): BinderDetailDto = orchestrator.detail(user, id)

    @PatchMapping("/{id}")
    fun rename(@CurrentUser user: User, @PathVariable id: Long, @Valid @RequestBody request: BinderRequest): BinderDetailDto =
        orchestrator.rename(user, id, request.name)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@CurrentUser user: User, @PathVariable id: Long) = orchestrator.delete(user, id)

    @PostMapping("/{id}/entries")
    fun add(@CurrentUser user: User, @PathVariable id: Long, @Valid @RequestBody request: BinderEntryRequest): CardBindersDto =
        orchestrator.add(user, id, request)

    /** 0 takes the card out of the binder. */
    @PutMapping("/{id}/entries/{entryId}")
    fun setQuantity(
        @CurrentUser user: User,
        @PathVariable id: Long,
        @PathVariable entryId: Long,
        @RequestBody request: BinderQuantityRequest,
    ): BinderDetailDto = orchestrator.setQuantity(user, id, entryId, request.quantity)

    @GetMapping("/cards/{cardId}")
    fun card(@CurrentUser user: User, @PathVariable cardId: Long): CardBindersDto = orchestrator.card(user, cardId)
}
