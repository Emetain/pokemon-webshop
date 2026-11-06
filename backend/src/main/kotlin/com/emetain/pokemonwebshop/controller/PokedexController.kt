package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CardListRequest
import com.emetain.pokemonwebshop.dto.CardPageDto
import com.emetain.pokemonwebshop.dto.PokedexDto
import com.emetain.pokemonwebshop.dto.PokedexEntryDto
import com.emetain.pokemonwebshop.orchestration.PokedexOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import org.springdoc.core.annotations.ParameterObject
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Public: a visitor who isn't logged in sees the same Pokédex with every slot empty. */
@RestController
@RequestMapping("/v1/pokedex")
class PokedexController(
    private val pokedexOrchestrator: PokedexOrchestrator,
) {

    @GetMapping
    fun pokedex(@CurrentUser viewer: User?): PokedexDto =
        pokedexOrchestrator.pokedex(viewer)

    @GetMapping("/{dexNumber}")
    fun entry(@PathVariable dexNumber: Int, @CurrentUser viewer: User?): PokedexEntryDto =
        pokedexOrchestrator.entry(dexNumber, viewer)

    @GetMapping("/{dexNumber}/cards")
    fun cards(
        @PathVariable dexNumber: Int,
        @ParameterObject request: CardListRequest,
        @CurrentUser viewer: User?,
    ): CardPageDto = pokedexOrchestrator.cards(dexNumber, request, viewer)
}
