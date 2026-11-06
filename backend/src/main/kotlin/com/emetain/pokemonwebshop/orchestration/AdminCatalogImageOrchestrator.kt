package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.dto.CatalogImageDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.service.CatalogImageService
import org.springframework.stereotype.Component

@Component
class AdminCatalogImageOrchestrator(
    private val imageService: CatalogImageService,
) {

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
}
