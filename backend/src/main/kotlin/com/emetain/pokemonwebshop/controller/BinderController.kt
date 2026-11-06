package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.BinderCardDto
import com.emetain.pokemonwebshop.dto.BinderPageDto
import com.emetain.pokemonwebshop.orchestration.BinderOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RequiresLogin
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/binder")
class BinderController(
    private val binderOrchestrator: BinderOrchestrator,
) {

    /** Public: a visitor who isn't logged in sees the popular cards. */
    @GetMapping
    fun page(@CurrentUser viewer: User?, @RequestParam(defaultValue = "0") page: Int): BinderPageDto =
        binderOrchestrator.page(viewer, page)

    @GetMapping("/selection")
    @RequiresLogin
    fun selection(@CurrentUser user: User): List<BinderCardDto> =
        binderOrchestrator.selection(user)

    @PutMapping("/selection/{cardId}")
    @RequiresLogin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun select(@CurrentUser user: User, @PathVariable cardId: Long) =
        binderOrchestrator.select(user, cardId)

    @DeleteMapping("/selection/{cardId}")
    @RequiresLogin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deselect(@CurrentUser user: User, @PathVariable cardId: Long) =
        binderOrchestrator.deselect(user, cardId)
}
