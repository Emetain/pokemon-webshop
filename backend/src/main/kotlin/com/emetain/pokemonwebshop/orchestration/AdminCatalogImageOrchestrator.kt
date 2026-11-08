package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.dto.AdminImageCardPageDto
import com.emetain.pokemonwebshop.dto.AdminImageCardRequest
import com.emetain.pokemonwebshop.dto.CatalogImageDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.AdminCardSort
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardSpecifications
import com.emetain.pokemonwebshop.service.CatalogImageService
import com.emetain.pokemonwebshop.service.CatalogText
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AdminCatalogImageOrchestrator(
    private val imageService: CatalogImageService,
    private val cardRepository: CardRepository,
) {

    /** Every card can be listed, so the admin can work through all cards without a picture. */
    @Transactional(readOnly = true)
    fun cards(request: AdminImageCardRequest): AdminImageCardPageDto {
        val game = request.game.filterValue()?.let(::game)
        val filters = listOfNotNull(
            request.q.filterValue()?.let(CatalogText::nameSearch)?.takeIf { it.isNotEmpty() }?.let(CardSpecifications::nameContains),
            CardSpecifications.ofGame(game),
            CardSpecifications.inSetWithCode(request.set.filterValue()),
            when (request.image.filterValue()) {
                MISSING -> CardSpecifications.withoutImage()
                CUSTOM -> CardSpecifications.withCustomImage()
                else -> null
            },
            CardSpecifications.sortedForAdmin(AdminCardSort.fromParam(request.sort.filterValue())),
        )
        val pageable = PageRequest.of(request.page.coerceAtLeast(0), PAGE_SIZE)
        return AdminImageCardPageDto.from(cardRepository.findAll(Specification.allOf(filters), pageable))
    }

    fun replaceCardImage(cardId: Long, bytes: ByteArray): CatalogImageDto =
        dto(imageService.replaceCardImage(cardId, bytes))

    fun resetCardImage(cardId: Long): CatalogImageDto =
        dto(imageService.resetCardImage(cardId))

    fun replaceSetLogo(gameSlug: String, code: String, bytes: ByteArray): CatalogImageDto =
        dto(imageService.replaceSetLogo(game(gameSlug), code, bytes))

    fun resetSetLogo(gameSlug: String, code: String): CatalogImageDto =
        dto(imageService.resetSetLogo(game(gameSlug), code))

    private fun dto(card: Card) = CatalogImageDto(card.shownImageSmallUrl, card.customImageUrl != null)

    private fun dto(set: CardSet) = CatalogImageDto(set.shownLogoUrl, set.customLogoUrl != null)

    private fun game(slug: String): Game = Game.fromSlug(slug) ?: throw ApiException.notFound()

    private companion object {
        const val PAGE_SIZE = 48
        const val MISSING = "missing"
        const val CUSTOM = "custom"
    }
}
