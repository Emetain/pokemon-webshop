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
import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.service.PremiumService
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.service.CollectionExport
import com.emetain.pokemonwebshop.service.CollectionPdf
import com.emetain.pokemonwebshop.service.ExchangeRateService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** A file to download: its name without the extension, and its contents. */
class CollectionFile(val name: String, val bytes: ByteArray)

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
    private val premiumService: PremiumService,
    private val cardPriceRepository: CardPriceRepository,
    private val exchangeRateService: ExchangeRateService,
    private val transaction: TransactionTemplate,
    private val clock: Clock,
    properties: AppProperties,
) {
    private val historyDaysWithoutPremium = properties.premium.historyDaysWithoutPremium


    @Transactional(readOnly = true)
    fun overview(user: User): CollectionOverviewDto = overview(user.id, user.currency)

    /** The value graph. Without Premium the API gives at most a year: the "Max" period is Premium (phase 11). */
    fun history(user: User): CollectionHistoryDto {
        val premium = premiumService.has(user)
        val history = historyService.history(user.id, user.currency, if (premium) null else historyDaysWithoutPremium)
        return CollectionHistoryDto(
            currency = user.currency,
            points = history.points.map { HistoryPointDto(it.date, it.cents) },
            premium = premium,
            hasOlder = history.hasOlder,
        )
    }

    fun startedSets(user: User, gameSlug: String): List<StartedSetDto> =
        statsService.startedSets(user.id, game(gameSlug)).map(StartedSetDto::from)

    fun set(user: User, gameSlug: String, code: String): SetProgressDto {
        val set = cardSetRepository.findByGameAndCode(game(gameSlug), code) ?: throw ApiException.notFound()
        return SetProgressDto.from(statsService.set(user.id, set.id, user.currency))
    }

    fun card(user: User, cardId: Long): CardCollectionDto {
        val wish = wishlistService.entry(user.id, cardId)
        return CardCollectionDto(
            entries = ownedEntries(user, cardId),
            onWishlist = wish != null,
            inBinder = binderService.isSelected(user.id, cardId),
            wishlistMaxPriceCents = wish?.maxPriceCents,
        )
    }

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

    /** The collection as CSV (Premium, phase 12): see [CollectionExport]. */
    fun exportCsv(user: User): CollectionFile {
        val table = exportTable(user)
        return CollectionFile(fileName(table.date), CollectionExport.csv(table).toByteArray(Charsets.UTF_8))
    }

    /** The collection as PDF (Premium, phase 12): see [CollectionPdf]. */
    fun exportPdf(user: User): CollectionFile {
        val table = exportTable(user)
        return CollectionFile(fileName(table.date), CollectionPdf.write(table))
    }

    private fun exportTable(user: User) = transaction.execute {
        premiumService.require(user, "Je collectie exporteren hoort bij Premium.")
        val entries = entryRepository.findAllOfUserWithCards(user.id)
        CollectionExport.table(
            ownerName = user.displayName,
            date = clock.instant().atZone(SHOP_ZONE).toLocalDate(),
            entries = entries,
            prices = if (entries.isEmpty()) emptyList() else cardPriceRepository.findAllByCardIds(entries.map { it.card.id }.toSet()),
            currency = user.currency,
            rates = exchangeRateService.current(),
        )
    }!!

    private fun fileName(date: LocalDate) = "poketopia-collectie-$date"

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

    private companion object {
        val SHOP_ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
    }
}
