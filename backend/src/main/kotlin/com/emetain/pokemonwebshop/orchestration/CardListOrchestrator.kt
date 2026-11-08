package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CardListRequest
import com.emetain.pokemonwebshop.dto.CardPageDto
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardSpecifications
import com.emetain.pokemonwebshop.repository.CollectionEntryRepository
import com.emetain.pokemonwebshop.service.CardOfferService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component

/** `?rarity=` means no filter, not "cards with an empty rarity". */
internal fun String?.filterValue(): String? = takeUnless { it.isNullOrBlank() }

/**
 * Two requests at the same moment can both see that a row doesn't exist yet, and one of them then breaks a unique
 * constraint. Its transaction is gone by then, so it simply starts over and now finds the row of the other one.
 */
internal fun <T> retryingOnConflict(action: () -> T): T =
    try {
        action()
    } catch (exception: DataIntegrityViolationException) {
        action()
    }

/** What every list of cards shares: the filters from the URL, and a page with what the viewer owns and the shop sells. */
@Component
class CardListOrchestrator(
    private val cardRepository: CardRepository,
    private val cardPriceRepository: CardPriceRepository,
    private val collectionEntryRepository: CollectionEntryRepository,
    private val cardOfferService: CardOfferService,
) {

    fun filters(request: CardListRequest, viewer: User?): List<Specification<Card>> = listOfNotNull(
        CardSpecifications.withRarity(request.rarity.filterValue()),
        CardSpecifications.withVariant(request.variant.filterValue()),
        CardSpecifications.withCategory(request.category.filterValue()),
        CardSpecifications.withColor(request.color.filterValue()),
        CardSpecifications.withPrice(request.priced),
        viewer?.let { collectionFilter(request, it) },
    )

    private fun collectionFilter(request: CardListRequest, viewer: User): Specification<Card>? =
        when (request.collection.filterValue()) {
            OWNED -> CardSpecifications.ownedBy(viewer.id)
            MISSING -> if (request.master) {
                CardSpecifications.variantMissingFor(viewer.id)
            } else {
                CardSpecifications.numberMissingFor(viewer.id)
            }
            else -> null
        }

    /** Call this inside a transaction: it reads the variants of the cards. */
    fun page(filters: List<Specification<Card>>, request: CardListRequest, viewer: User?): CardPageDto {
        val pageable = PageRequest.of(request.page.coerceAtLeast(0), request.size.coerceIn(1, MAX_PAGE_SIZE))
        val cards = cardRepository.findAll(Specification.allOf(filters), pageable)
        val cardIds = cards.content.map { it.id }
        val prices = cardPriceRepository.findAllByCardIds(cardIds)
        val owned = viewer?.let { user ->
            collectionEntryRepository.findAllByUserAndCards(user.id, cardIds).groupBy { it.card.id }
        }
        return CardPageDto.from(cards, prices.groupBy { it.card.id }, owned, cardOfferService.cheapestPerCard(cardIds))
    }

    private companion object {
        const val MAX_PAGE_SIZE = 100
        const val OWNED = "owned"
        const val MISSING = "missing"
    }
}
