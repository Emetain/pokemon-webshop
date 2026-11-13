package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.SuggestionStatus
import com.emetain.pokemonwebshop.dto.AcceptPictureRequest
import com.emetain.pokemonwebshop.dto.PictureSearchOverviewDto
import com.emetain.pokemonwebshop.dto.PictureSearchStatusDto
import com.emetain.pokemonwebshop.dto.PictureSuggestionDto
import com.emetain.pokemonwebshop.dto.PictureSuggestionPageDto
import com.emetain.pokemonwebshop.dto.ReleaseSuggestionDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.PictureSuggestionRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.security.AdminOnly
import com.emetain.pokemonwebshop.service.ReleaseSuggestionService
import com.emetain.pokemonwebshop.service.pictures.PictureSearchService
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Pictures from TCGplayer for cards and products without one: start a search per game, follow it, and decide on the
 * doubtful matches.
 */
@RestController
@RequestMapping("/v1/admin/pictures")
@AdminOnly
class AdminPictureController(
    private val pictureSearchService: PictureSearchService,
    private val cardRepository: CardRepository,
    private val productRepository: ProductRepository,
    private val suggestionRepository: PictureSuggestionRepository,
) {

    @GetMapping("/search")
    @Transactional(readOnly = true)
    fun overview(): List<PictureSearchOverviewDto> = Game.entries.map { game ->
        PictureSearchOverviewDto(
            game = game.slug,
            cardsWithoutPicture = cardRepository.countWithoutPicture(game),
            productsWithoutPicture = productRepository.countSealedWithoutPicture(game),
            pendingSuggestions = suggestionRepository.countByGameAndStatus(game, SuggestionStatus.PENDING),
            search = pictureSearchService.status(game)?.let(PictureSearchStatusDto::from),
        )
    }

    @PostMapping("/search/{game}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(@PathVariable game: String): PictureSearchStatusDto = PictureSearchStatusDto.from(pictureSearchService.start(game(game)))

    /** The open suggestions, all games or one. The reply is made inside the transaction: it reads the cards and sets. */
    @GetMapping("/suggestions")
    @Transactional(readOnly = true)
    fun suggestions(@RequestParam(required = false) game: String?, @RequestParam(defaultValue = "0") page: Int): PictureSuggestionPageDto {
        val result = pictureSearchService.pending(game?.takeIf { it.isNotBlank() }?.let(::game), page)
        return PictureSuggestionPageDto(result.content.map(PictureSuggestionDto::from), result.number, result.totalPages, result.totalElements)
    }

    @PostMapping("/suggestions/{id}/accept")
    @Transactional
    fun accept(@PathVariable id: Long, @RequestBody request: AcceptPictureRequest): PictureSuggestionDto =
        PictureSuggestionDto.from(pictureSearchService.accept(id, request.tcgplayerId))

    @PostMapping("/suggestions/{id}/reject")
    @Transactional
    fun reject(@PathVariable id: Long): PictureSuggestionDto = PictureSuggestionDto.from(pictureSearchService.reject(id))

    private fun game(slug: String): Game = Game.fromSlug(slug) ?: throw ApiException.validation("Kies een geldig spel.")
}

/** Upcoming sets the card sources and TCGplayer know, for the release calendar. */
@RestController
@RequestMapping("/v1/admin/release-suggestions")
@AdminOnly
class AdminReleaseSuggestionController(
    private val releaseSuggestionService: ReleaseSuggestionService,
) {

    @GetMapping
    fun pending(): List<ReleaseSuggestionDto> = releaseSuggestionService.pending().map(ReleaseSuggestionDto::from)

    /** Asks the sources again now instead of waiting for the night. */
    @PostMapping("/refresh")
    fun refresh(): List<ReleaseSuggestionDto> {
        releaseSuggestionService.refresh()
        return pending()
    }

    @PostMapping("/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun dismiss(@PathVariable id: Long) = releaseSuggestionService.dismiss(id)
}
