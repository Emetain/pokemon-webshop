package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.User
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
import com.emetain.pokemonwebshop.dto.ProductSummaryDto
import com.emetain.pokemonwebshop.service.CardOfferService
import com.emetain.pokemonwebshop.service.CatalogText
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CatalogOrchestrator(
    private val cardSetRepository: CardSetRepository,
    private val cardRepository: CardRepository,
    private val cardPriceRepository: CardPriceRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val cardList: CardListOrchestrator,
    private val cardOfferService: CardOfferService,
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
    fun cards(gameSlug: String, code: String, request: CardListRequest, viewer: User?): CardPageDto {
        val sort = CardSort.fromParam(request.sort.filterValue()) ?: CardSort.NUMBER
        val filters = cardList.filters(request, viewer) +
            CardSpecifications.inSet(findSet(gameSlug, code)) +
            CardSpecifications.sortedBy(sort)
        return cardList.page(filters, request, viewer)
    }

    @Transactional(readOnly = true)
    fun card(id: Long): CardDetailDto {
        val card = cardRepository.findWithSetById(id) ?: throw ApiException.notFound()
        val offers = cardOfferService.buyable(listOf(id)).map { ProductSummaryDto.from(it) }
        return CardDetailDto.from(card, cardPriceRepository.findAllByCardIds(listOf(id)), offers)
    }

    @Transactional(readOnly = true)
    fun search(
        query: String,
        gameSlug: String?,
        setCode: String?,
        request: CardListRequest,
        viewer: User?,
    ): CardSearchResultDto {
        val term = CatalogText.nameSearch(query)
        if (term.length < MIN_SEARCH_LENGTH) {
            throw ApiException.validation("Vul minstens $MIN_SEARCH_LENGTH tekens in om te zoeken.")
        }
        val game = gameSlug.filterValue()?.let(::game)
        val sort = CardSort.fromParam(request.sort.filterValue()) ?: CardSort.BEST_MATCH
        val filters = cardList.filters(request, viewer) + listOfNotNull(
            CardSpecifications.nameContains(term),
            CardSpecifications.ofGame(game),
            CardSpecifications.inSetWithCode(setCode.filterValue()),
            CardSpecifications.sortedBy(sort, term),
        )
        return CardSearchResultDto(
            results = cardList.page(filters, request, viewer),
            rarities = game?.let(cardRepository::findRaritiesInGame).orEmpty(),
            categories = game?.let(cardRepository::findCategoriesInGame).orEmpty(),
            colors = game?.let(cardRepository::findColorsInGame).orEmpty(),
        )
    }

    fun exchangeRates(): List<ExchangeRateDto> =
        exchangeRateRepository.findAll().map(ExchangeRateDto::from)

    private fun findSet(gameSlug: String, code: String): CardSet =
        cardSetRepository.findByGameAndCode(game(gameSlug), code) ?: throw ApiException.notFound()

    private fun game(slug: String): Game = Game.fromSlug(slug) ?: throw ApiException.notFound()

    private companion object {
        const val MIN_SEARCH_LENGTH = 2
    }
}
