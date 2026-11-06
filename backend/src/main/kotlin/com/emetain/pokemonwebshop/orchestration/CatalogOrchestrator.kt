package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.dto.CardDetailDto
import com.emetain.pokemonwebshop.dto.CardListRequest
import com.emetain.pokemonwebshop.dto.CardPageDto
import com.emetain.pokemonwebshop.dto.CardSearchResultDto
import com.emetain.pokemonwebshop.dto.CardSetDetailDto
import com.emetain.pokemonwebshop.dto.CardSetDto
import com.emetain.pokemonwebshop.dto.ExchangeRateDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.CardSort
import com.emetain.pokemonwebshop.repository.CardSpecifications
import com.emetain.pokemonwebshop.repository.ExchangeRateRepository
import com.emetain.pokemonwebshop.service.CatalogText
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CatalogOrchestrator(
    private val cardSetRepository: CardSetRepository,
    private val cardRepository: CardRepository,
    private val cardPriceRepository: CardPriceRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
) {

    fun sets(gameSlug: String): List<CardSetDto> =
        cardSetRepository.findAllNewestFirst(game(gameSlug)).map(CardSetDto::from)

    fun set(gameSlug: String, code: String): CardSetDetailDto {
        val set = findSet(gameSlug, code)
        return CardSetDetailDto(
            set = CardSetDto.from(set),
            rarities = cardRepository.findRaritiesInSet(set.id),
            variants = CardVariants.displaySorted(cardRepository.findVariantsInSet(set.id)),
            categories = cardRepository.findCategoriesInSet(set.id),
            colors = cardRepository.findColorsInSet(set.id),
        )
    }

    @Transactional(readOnly = true)
    fun cards(gameSlug: String, code: String, request: CardListRequest): CardPageDto {
        val sort = CardSort.fromParam(request.sort.filterValue()) ?: CardSort.NUMBER
        val filters = filters(request) + CardSpecifications.inSet(findSet(gameSlug, code)) + CardSpecifications.sortedBy(sort)
        return cardPage(filters, request)
    }

    @Transactional(readOnly = true)
    fun card(id: Long): CardDetailDto {
        val card = cardRepository.findWithSetById(id) ?: throw ApiException.notFound()
        return CardDetailDto.from(card, cardPriceRepository.findAllByCardIds(listOf(id)))
    }

    @Transactional(readOnly = true)
    fun search(query: String, gameSlug: String?, setCode: String?, request: CardListRequest): CardSearchResultDto {
        val term = CatalogText.nameSearch(query)
        if (term.length < MIN_SEARCH_LENGTH) {
            throw ApiException.validation("Vul minstens $MIN_SEARCH_LENGTH tekens in om te zoeken.")
        }
        val game = gameSlug.filterValue()?.let(::game)
        val sort = CardSort.fromParam(request.sort.filterValue()) ?: CardSort.BEST_MATCH
        val filters = filters(request) + listOfNotNull(
            CardSpecifications.nameContains(term),
            CardSpecifications.ofGame(game),
            CardSpecifications.inSetWithCode(setCode.filterValue()),
            CardSpecifications.sortedBy(sort, term),
        )
        return CardSearchResultDto(
            results = cardPage(filters, request),
            rarities = game?.let(cardRepository::findRaritiesInGame).orEmpty(),
            categories = game?.let(cardRepository::findCategoriesInGame).orEmpty(),
            colors = game?.let(cardRepository::findColorsInGame).orEmpty(),
        )
    }

    fun exchangeRates(): List<ExchangeRateDto> =
        exchangeRateRepository.findAll().map(ExchangeRateDto::from)

    private fun filters(request: CardListRequest): List<Specification<Card>> = listOfNotNull(
        CardSpecifications.withRarity(request.rarity.filterValue()),
        CardSpecifications.withVariant(request.variant.filterValue()),
        CardSpecifications.withCategory(request.category.filterValue()),
        CardSpecifications.withColor(request.color.filterValue()),
        CardSpecifications.withPrice(request.priced),
    )

    private fun cardPage(filters: List<Specification<Card>>, request: CardListRequest): CardPageDto {
        val pageable = PageRequest.of(request.page.coerceAtLeast(0), request.size.coerceIn(1, MAX_PAGE_SIZE))
        val cards: Page<Card> = cardRepository.findAll(Specification.allOf(filters), pageable)
        val prices = cardPriceRepository.findAllByCardIds(cards.content.map { it.id })
        return CardPageDto.from(cards, prices.groupBy { it.card.id })
    }

    private fun findSet(gameSlug: String, code: String): CardSet =
        cardSetRepository.findByGameAndCode(game(gameSlug), code) ?: throw ApiException.notFound()

    private fun game(slug: String): Game = Game.fromSlug(slug) ?: throw ApiException.notFound()

    /** `?rarity=` means no filter, not "cards with an empty rarity". */
    private fun String?.filterValue(): String? = takeUnless { it.isNullOrBlank() }

    private companion object {
        const val MIN_SEARCH_LENGTH = 2
        const val MAX_PAGE_SIZE = 100
    }
}
