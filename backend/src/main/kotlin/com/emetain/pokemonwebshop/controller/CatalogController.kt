package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CardDetailDto
import com.emetain.pokemonwebshop.dto.CardPriceHistoryDto
import com.emetain.pokemonwebshop.dto.CardListRequest
import com.emetain.pokemonwebshop.dto.CardPageDto
import com.emetain.pokemonwebshop.dto.CardSearchResultDto
import com.emetain.pokemonwebshop.dto.CardSetDetailDto
import com.emetain.pokemonwebshop.dto.CardSetDto
import com.emetain.pokemonwebshop.dto.ExchangeRateDto
import com.emetain.pokemonwebshop.orchestration.CatalogOrchestrator
import com.emetain.pokemonwebshop.orchestration.PriceTrendOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RequiresLogin
import org.springdoc.core.annotations.ParameterObject
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/catalog")
class CatalogController(
    private val catalogOrchestrator: CatalogOrchestrator,
    private val priceTrendOrchestrator: PriceTrendOrchestrator,
) {

    @GetMapping("/{game}/sets")
    fun sets(@PathVariable game: String): List<CardSetDto> =
        catalogOrchestrator.sets(game)

    @GetMapping("/{game}/sets/{code}")
    fun set(@PathVariable game: String, @PathVariable code: String): CardSetDetailDto =
        catalogOrchestrator.set(game, code)

    @GetMapping("/{game}/sets/{code}/cards")
    fun cards(
        @PathVariable game: String,
        @PathVariable code: String,
        @ParameterObject request: CardListRequest,
        @CurrentUser viewer: User?,
    ): CardPageDto = catalogOrchestrator.cards(game, code, request, viewer)

    @GetMapping("/cards/{id}")
    fun card(@PathVariable id: Long): CardDetailDto =
        catalogOrchestrator.card(id)

    /** The price graph of a card, per variant (Premium, phase 12). */
    @GetMapping("/cards/{id}/history")
    @RequiresLogin
    fun cardHistory(@CurrentUser user: User, @PathVariable id: Long): CardPriceHistoryDto =
        priceTrendOrchestrator.cardHistory(user, id)

    @GetMapping("/search")
    fun search(
        @RequestParam(defaultValue = "") q: String,
        @RequestParam(required = false) game: String?,
        @RequestParam(required = false) set: String?,
        @ParameterObject request: CardListRequest,
        @CurrentUser viewer: User?,
    ): CardSearchResultDto = catalogOrchestrator.search(q, game, set, request, viewer)

    @GetMapping("/exchange-rates")
    fun exchangeRates(): List<ExchangeRateDto> =
        catalogOrchestrator.exchangeRates()
}
