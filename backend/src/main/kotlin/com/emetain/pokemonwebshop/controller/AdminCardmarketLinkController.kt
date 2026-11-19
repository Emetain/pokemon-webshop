package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.CardmarketLink
import com.emetain.pokemonwebshop.dto.CardmarketLinkDto
import com.emetain.pokemonwebshop.dto.CardmarketLinkPageDto
import com.emetain.pokemonwebshop.dto.SetCardmarketLinkRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.security.AdminOnly
import com.emetain.pokemonwebshop.service.CardmarketLinkService
import com.emetain.pokemonwebshop.service.LinkReview
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Pokémon cards whose Cardmarket product is doubtful, and the ones an admin linked by hand (phase 11). */
@RestController
@RequestMapping("/v1/admin/catalog/cardmarket-links")
@AdminOnly
class AdminCardmarketLinkController(
    private val linkService: CardmarketLinkService,
) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "doubtful") status: String,
        @RequestParam(defaultValue = "0") page: Int,
    ): CardmarketLinkPageDto {
        val link = when (status) {
            "doubtful" -> CardmarketLink.DOUBTFUL
            "manual" -> CardmarketLink.MANUAL
            else -> throw ApiException.validation("Kies twijfelachtig of met de hand.")
        }
        val reviews = linkService.reviews(link, page)
        return CardmarketLinkPageDto(
            items = reviews.content.map(::dto),
            page = reviews.number,
            totalPages = reviews.totalPages,
            totalItems = reviews.totalElements,
            doubtful = linkService.count(CardmarketLink.DOUBTFUL),
            manual = linkService.count(CardmarketLink.MANUAL),
        )
    }

    @PutMapping("/{cardId}")
    fun set(@PathVariable cardId: Long, @RequestBody request: SetCardmarketLinkRequest): CardmarketLinkDto =
        dto(linkService.setManual(cardId, request.productId))

    @DeleteMapping("/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset(@PathVariable cardId: Long) = linkService.reset(cardId)

    private fun dto(review: LinkReview) = CardmarketLinkDto.from(review.card, review.prices, review.product, review.candidates)
}
