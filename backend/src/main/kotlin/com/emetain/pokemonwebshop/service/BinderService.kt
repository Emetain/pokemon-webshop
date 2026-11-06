package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.BinderSelection
import com.emetain.pokemonwebshop.domain.BinderSource
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.BinderCardDto
import com.emetain.pokemonwebshop.dto.BinderPageDto
import com.emetain.pokemonwebshop.dto.BinderSlotDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.BinderSelectionRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CollectionEntryRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * The 9-pocket binder in the home hero. Visitors see the most expensive cards in general, and customers their
 * own most expensive cards until they choose something else. "Popular" means the cards in the most collections.
 * A new shop has no collections yet, so the most expensive Pokémon cards fill up that list.
 */
@Service
class BinderService(
    private val cardRepository: CardRepository,
    private val entryRepository: CollectionEntryRepository,
    private val selectionRepository: BinderSelectionRepository,
    private val pokedexService: PokedexService,
    properties: AppProperties,
    private val clock: Clock,
) {

    // Every visitor of the home page asks for these, and they're the same for everyone.
    private val popular = TimedCache(properties.collection.cacheTtl, clock, ::loadPopular)
    private val mostExpensive = TimedCache(properties.collection.cacheTtl, clock, ::loadMostExpensive)

    @Transactional(readOnly = true)
    fun page(user: User?, page: Int): BinderPageDto {
        val index = page.coerceAtLeast(0)
        if (user == null) return mostExpensive(index)

        val result = when (user.binderSource ?: BinderSource.DEFAULT) {
            BinderSource.POPULAR -> slice(BinderSource.POPULAR, popular.get(), index)
            BinderSource.MOST_EXPENSIVE -> mostExpensive(index)
            BinderSource.MOST_EXPENSIVE_OWNED -> mostExpensiveOwned(user, index)
            BinderSource.OWN_SELECTION -> ownSelection(user, index)
            BinderSource.POKEDEX -> pokedex(user, index)
        }
        // Someone who chose a source always sees that source, even while it's empty.
        val nothingChosenAndNothingOwned = user.binderSource == null && result.totalPages == 0
        return if (nothingChosenAndNothingOwned) mostExpensive(index).copy(fallback = true) else result
    }

    private fun mostExpensive(page: Int) = slice(BinderSource.MOST_EXPENSIVE, mostExpensive.get(), page)

    @Transactional(readOnly = true)
    fun selection(userId: Long): List<BinderCardDto> =
        selectionRepository.findAllByUserIdOrderById(userId).map { BinderCardDto.from(it.card) }

    @Transactional(readOnly = true)
    fun isSelected(userId: Long, cardId: Long): Boolean = selectionRepository.existsByUserIdAndCardId(userId, cardId)

    @Transactional
    fun select(user: User, cardId: Long) {
        if (selectionRepository.existsByUserIdAndCardId(user.id, cardId)) return
        if (selectionRepository.countByUserId(user.id) >= MAX_SELECTED_CARDS) {
            throw ApiException.validation("Je binder zit vol: je kunt maximaal $MAX_SELECTED_CARDS kaarten kiezen.")
        }
        val card = cardRepository.findById(cardId).orElseThrow { ApiException.notFound("We konden deze kaart niet vinden.") }
        selectionRepository.saveAndFlush(BinderSelection(user, card, clock.instant()))
    }

    @Transactional
    fun deselect(userId: Long, cardId: Long) {
        selectionRepository.findByUserIdAndCardId(userId, cardId)?.let(selectionRepository::delete)
    }

    private fun mostExpensiveOwned(user: User, page: Int): BinderPageDto {
        val cards = cardRepository.findOwnedMostExpensiveFirst(user.id, PageRequest.of(page, SLOTS_PER_PAGE))
        val slots = cards.content.map { BinderSlotDto(BinderCardDto.from(it)) }
        return BinderPageDto(BinderSource.MOST_EXPENSIVE_OWNED, false, page, cards.totalPages, slots)
    }

    private fun ownSelection(user: User, page: Int): BinderPageDto {
        val selection = selectionRepository.findAllByUserIdOrderById(user.id, PageRequest.of(page, SLOTS_PER_PAGE))
        val slots = selection.content.map { BinderSlotDto(BinderCardDto.from(it.card)) }
        return BinderPageDto(BinderSource.OWN_SELECTION, false, page, selection.totalPages, slots)
    }

    private fun pokedex(user: User, page: Int): BinderPageDto {
        val slots = pokedexService.pokedex(user.id).slots
        return BinderPageDto(
            source = BinderSource.POKEDEX,
            fallback = false,
            page = page,
            totalPages = pageCount(slots.size),
            slots = slots.drop(page * SLOTS_PER_PAGE).take(SLOTS_PER_PAGE).map { slot ->
                val card = slot.card?.let { BinderCardDto(it.cardId, it.game.slug, it.setCode, slot.name, it.imageUrl) }
                BinderSlotDto(card, slot.dexNumber, slot.name)
            },
        )
    }

    private fun slice(source: BinderSource, cards: List<BinderCardDto>, page: Int) = BinderPageDto(
        source = source,
        fallback = false,
        page = page,
        totalPages = pageCount(cards.size),
        slots = cards.drop(page * SLOTS_PER_PAGE).take(SLOTS_PER_PAGE).map { BinderSlotDto(it) },
    )

    private fun pageCount(cards: Int) = (cards + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE

    private fun loadPopular(): List<BinderCardDto> {
        val ids = entryRepository.findMostCollectedCardIds(TOP_CARDS)
        val cards = cardRepository.findAllWithSetByIdIn(ids).associateBy { it.id }
        val collected = ids.mapNotNull(cards::get)

        val fill = cardRepository.findMostExpensiveOfGame(Game.POKEMON, TOP_CARDS).filter { it.id !in cards }
        return (collected + fill).take(TOP_CARD_COUNT).map(BinderCardDto::from)
    }

    private fun loadMostExpensive(): List<BinderCardDto> =
        cardRepository.findMostExpensive(TOP_CARDS).map(BinderCardDto::from)

    private companion object {
        const val SLOTS_PER_PAGE = 9
        const val TOP_CARD_COUNT = 5 * SLOTS_PER_PAGE
        const val MAX_SELECTED_CARDS = 9 * SLOTS_PER_PAGE
        val TOP_CARDS: PageRequest = PageRequest.of(0, TOP_CARD_COUNT)
    }
}
