package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AddCollectionEntryRequest
import com.emetain.pokemonwebshop.dto.BulkAddRequest
import com.emetain.pokemonwebshop.dto.BulkAddResultDto
import com.emetain.pokemonwebshop.dto.CardCollectionDto
import com.emetain.pokemonwebshop.dto.CollectionChangeDto
import com.emetain.pokemonwebshop.dto.CollectionHistoryDto
import com.emetain.pokemonwebshop.dto.CollectionOverviewDto
import com.emetain.pokemonwebshop.dto.HistoryPointDto
import com.emetain.pokemonwebshop.dto.OwnedEntryDto
import com.emetain.pokemonwebshop.dto.SetProgressDto
import com.emetain.pokemonwebshop.dto.SharedCollectionDto
import com.emetain.pokemonwebshop.dto.StartedSetDto
import com.emetain.pokemonwebshop.dto.UpdateCollectionEntryRequest
import com.emetain.pokemonwebshop.dto.UserDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.CollectionEntryRepository
import com.emetain.pokemonwebshop.service.BinderService
import com.emetain.pokemonwebshop.service.CardWishlistService
import com.emetain.pokemonwebshop.service.CollectionHistoryService
import com.emetain.pokemonwebshop.service.CollectionService
import com.emetain.pokemonwebshop.service.CollectionStatsService
import com.emetain.pokemonwebshop.service.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CollectionOrchestrator(
    private val collectionService: CollectionService,
    private val statsService: CollectionStatsService,
    private val historyService: CollectionHistoryService,
    private val wishlistService: CardWishlistService,
    private val binderService: BinderService,
    private val userService: UserService,
    private val cardRepository: CardRepository,
    private val cardSetRepository: CardSetRepository,
    private val entryRepository: CollectionEntryRepository,
) {

    @Transactional(readOnly = true)
    fun overview(user: User): CollectionOverviewDto = overview(user.id, user.currency)

    fun history(user: User): CollectionHistoryDto = CollectionHistoryDto(
        currency = user.currency,
        points = historyService.history(user.id, user.currency).map { HistoryPointDto(it.date, it.cents) },
    )

    fun startedSets(user: User, gameSlug: String): List<StartedSetDto> =
        statsService.startedSets(user.id, game(gameSlug)).map(StartedSetDto::from)

    fun set(user: User, gameSlug: String, code: String): SetProgressDto {
        val set = cardSetRepository.findByGameAndCode(game(gameSlug), code) ?: throw ApiException.notFound()
        return SetProgressDto.from(statsService.set(user.id, set.id, user.currency))
    }

    fun card(user: User, cardId: Long): CardCollectionDto = CardCollectionDto(
        entries = ownedEntries(user, cardId),
        onWishlist = wishlistService.contains(user.id, cardId),
        inBinder = binderService.isSelected(user.id, cardId),
    )

    fun addEntry(user: User, request: AddCollectionEntryRequest): CollectionChangeDto {
        retryingOnConflict {
            collectionService.add(user, request.cardId, request.variant, request.quantity, request.condition)
        }
        return change(user, request.cardId)
    }

    fun updateEntry(user: User, entryId: Long, request: UpdateCollectionEntryRequest): CollectionChangeDto =
        change(user, collectionService.update(user.id, entryId, request.quantity, request.condition).card.id)

    fun removeEntry(user: User, entryId: Long): CollectionChangeDto =
        change(user, collectionService.remove(user.id, entryId))

    fun addBulk(user: User, request: BulkAddRequest): BulkAddResultDto =
        BulkAddResultDto.from(retryingOnConflict { collectionService.addAll(user, request.cardIds) })

    fun share(user: User): UserDto = UserDto.from(userService.shareCollection(user.id))

    fun stopSharing(user: User): UserDto = UserDto.from(userService.stopSharingCollection(user.id))

    /** Prices are in the currency of whoever is looking, not of the owner. */
    @Transactional(readOnly = true)
    fun shared(slug: String, viewer: User?): SharedCollectionDto {
        val owner = userService.findBySharedCollection(slug) ?: throw ApiException.notFound()
        return SharedCollectionDto(
            ownerName = owner.displayName.trim().substringBefore(' '),
            collection = overview(owner.id, viewer?.currency ?: Currency.EUR),
        )
    }

    private fun overview(userId: Long, currency: Currency): CollectionOverviewDto {
        val stats = statsService.overview(userId, currency)
        val cards = cardRepository.findAllWithSetByIdIn(stats.mostValuable.map { it.cardId }).associateBy { it.id }
        return CollectionOverviewDto.from(stats, cards)
    }

    private fun change(user: User, cardId: Long): CollectionChangeDto {
        val card = cardRepository.findWithSetById(cardId) ?: throw ApiException.notFound()
        return CollectionChangeDto(
            entries = ownedEntries(user, cardId),
            progress = SetProgressDto.from(statsService.set(user.id, card.set.id, user.currency)),
        )
    }

    private fun ownedEntries(user: User, cardId: Long): List<OwnedEntryDto> =
        OwnedEntryDto.listOf(entryRepository.findAllByUserAndCards(user.id, listOf(cardId)))

    private fun game(slug: String): Game = Game.fromSlug(slug) ?: throw ApiException.notFound()
}
