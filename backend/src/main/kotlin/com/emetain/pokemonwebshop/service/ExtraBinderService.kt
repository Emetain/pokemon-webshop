package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.ExtraBinder
import com.emetain.pokemonwebshop.domain.ExtraBinderEntry
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.ExtraBinderEntryRepository
import com.emetain.pokemonwebshop.repository.ExtraBinderRepository
import com.emetain.pokemonwebshop.repository.OwnedLine
import com.emetain.pokemonwebshop.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** A binder with its cards and what they're worth together. */
data class BinderContents(
    val binder: ExtraBinder,
    val entries: List<ExtraBinderEntry>,
    val prices: Map<Pair<Long, String>, Price>,
    val value: CollectionValue,
)

/** A binder in the list: how many copies and what they're worth. */
data class BinderSummary(val binder: ExtraBinder, val copies: Long, val value: CollectionValue)

/**
 * Extra binders (Premium, phase 12), next to the collection. Making, renaming, filling and removing them is Premium.
 * When Premium ends they stay visible but can't be changed, and nothing is deleted.
 */
@Service
class ExtraBinderService(
    private val binderRepository: ExtraBinderRepository,
    private val entryRepository: ExtraBinderEntryRepository,
    private val cardRepository: CardRepository,
    private val cardPriceRepository: CardPriceRepository,
    private val userRepository: UserRepository,
    private val premiumService: PremiumService,
    private val exchangeRateService: ExchangeRateService,
    private val clock: Clock,
    properties: AppProperties,
) {
    private val maxBinders = properties.premium.maxBinders
    private val maxCards = properties.premium.maxBinderCards

    @Transactional(readOnly = true)
    fun list(userId: Long, currency: Currency): List<BinderSummary> {
        val binders = binderRepository.findAllByUserIdOrderByCreatedAtAscIdAsc(userId)
        if (binders.isEmpty()) return emptyList()
        val entries = entryRepository.findAllOfUser(userId)
        val prices = prices(entries.map { it.card.id })
        val rates = exchangeRateService.current()
        val byBinder = entries.groupBy { it.binder.id }
        return binders.map { binder ->
            val own = byBinder[binder.id].orEmpty()
            BinderSummary(binder, own.sumOf { it.quantity.toLong() }, value(own, prices, currency, rates))
        }
    }

    @Transactional(readOnly = true)
    fun contents(userId: Long, binderId: Long, currency: Currency): BinderContents {
        val binder = binderRepository.findByIdAndUserId(binderId, userId) ?: throw ApiException.notFound("Deze binder bestaat niet.")
        val entries = entryRepository.findAllOfBinder(binder.id)
        val prices = prices(entries.map { it.card.id })
        return BinderContents(binder, entries, prices, value(entries, prices, currency, exchangeRateService.current()))
    }

    /** Which of the customer's binders hold this card, per variant: for the card page. */
    @Transactional(readOnly = true)
    fun entriesOfCard(userId: Long, cardId: Long): List<ExtraBinderEntry> = entryRepository.findAllOfUserAndCard(userId, cardId)

    @Transactional
    fun create(user: User, name: String): ExtraBinder {
        requirePremium(user)
        if (binderRepository.countByUserId(user.id) >= maxBinders) {
            throw ApiException.validation("Je kunt maximaal $maxBinders binders maken.")
        }
        val now = clock.instant()
        return binderRepository.save(ExtraBinder(userRepository.getReferenceById(user.id), cleanName(name), now, now))
    }

    @Transactional
    fun rename(user: User, binderId: Long, name: String): ExtraBinder {
        requirePremium(user)
        val binder = owned(user, binderId)
        binder.name = cleanName(name)
        binder.updatedAt = clock.instant()
        return binder
    }

    @Transactional
    fun delete(user: User, binderId: Long) {
        requirePremium(user)
        val binder = owned(user, binderId)
        entryRepository.deleteAllByBinderId(binder.id)
        binderRepository.delete(binder)
    }

    /** Adds [quantity] copies, on top of what the binder already has. Without a variant it's the card's main one. */
    @Transactional
    fun add(user: User, binderId: Long, cardId: Long, variant: String?, quantity: Int): ExtraBinderEntry {
        requirePremium(user)
        requireQuantity(quantity)
        val binder = owned(user, binderId)
        val card = cardRepository.findWithSetById(cardId) ?: throw ApiException.notFound("Deze kaart bestaat niet.")
        val chosen = variantOf(card, variant)
        val existing = entryRepository.findByBinderIdAndCardIdAndVariant(binder.id, card.id, chosen)
        binder.updatedAt = clock.instant()
        if (existing != null) {
            requireQuantity(existing.quantity + quantity)
            existing.quantity += quantity
            return existing
        }
        if (entryRepository.countByBinderId(binder.id) >= maxCards) {
            throw ApiException.validation("Een binder kan maximaal $maxCards verschillende kaarten hebben.")
        }
        return try {
            entryRepository.saveAndFlush(ExtraBinderEntry(binder, card, chosen, quantity, clock.instant()))
        } catch (exception: DataIntegrityViolationException) {
            throw ApiException.validation("Deze kaart zit al in de binder. Probeer het opnieuw.")
        }
    }

    /** Sets the number of copies; 0 takes the card out of the binder. */
    @Transactional
    fun setQuantity(user: User, binderId: Long, entryId: Long, quantity: Int) {
        requirePremium(user)
        val binder = owned(user, binderId)
        val entry = entryRepository.findByIdAndBinderId(entryId, binder.id) ?: throw ApiException.notFound()
        binder.updatedAt = clock.instant()
        if (quantity == 0) {
            entryRepository.delete(entry)
            return
        }
        requireQuantity(quantity)
        entry.quantity = quantity
    }

    private fun requirePremium(user: User) =
        premiumService.require(user, "Extra binders maken en aanpassen hoort bij Premium. Je binders blijven zichtbaar.")

    private fun owned(user: User, binderId: Long) =
        binderRepository.findByIdAndUserId(binderId, user.id) ?: throw ApiException.notFound("Deze binder bestaat niet.")

    private fun cleanName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) throw ApiException.validation("Geef je binder een naam.")
        if (clean.length > MAX_NAME) throw ApiException.validation("De naam mag maximaal $MAX_NAME tekens lang zijn.")
        return clean
    }

    private fun requireQuantity(quantity: Int) {
        if (quantity !in 1..MAX_QUANTITY) throw ApiException.validation("Kies een aantal van 1 tot en met $MAX_QUANTITY.")
    }

    private fun prices(cardIds: Collection<Long>): Map<Pair<Long, String>, Price> =
        if (cardIds.isEmpty()) emptyMap() else CurrentPrices.of(cardPriceRepository.findAllByCardIds(cardIds.toSet()))

    private fun value(
        entries: List<ExtraBinderEntry>,
        prices: Map<Pair<Long, String>, Price>,
        currency: Currency,
        rates: ExchangeRates,
    ): CollectionValue = CollectionValueCalculator.value(
        entries.map { entry ->
            val price = prices[entry.card.id to entry.variant]
            OwnedLine(entry.card.id, entry.card.game, entry.card.set.id, entry.variant, entry.quantity, price?.cents, price?.currency)
        },
        currency,
        rates,
    )

    private companion object {
        const val MAX_NAME = 60
        const val MAX_QUANTITY = 9999
    }
}
