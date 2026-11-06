package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardCondition
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.CollectionEntry
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CollectionEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

data class BulkAddResult(val added: Int, val skipped: Int)

/** Changes what a customer owns. */
@Service
class CollectionService(
    private val entryRepository: CollectionEntryRepository,
    private val cardRepository: CardRepository,
    private val clock: Clock,
) {

    /** Adds [quantity] copies, on top of what the customer already has. Without a variant it's the card's default. */
    @Transactional
    fun add(user: User, cardId: Long, variant: String?, quantity: Int, condition: CardCondition?): CollectionEntry {
        requireUsableQuantity(quantity)
        val card = cardRepository.findById(cardId).orElseThrow { ApiException.notFound("We konden deze kaart niet vinden.") }
        val chosenVariant = variant ?: CardVariants.default(card.variants)
        if (chosenVariant !in card.variants) {
            throw ApiException.validation("Deze kaart bestaat niet in deze variant.")
        }

        val existing = entryRepository.findForUpdate(user.id, cardId, chosenVariant)
        if (existing != null) {
            requireUsableQuantity(existing.quantity + quantity)
            existing.quantity += quantity
            condition?.let { existing.condition = it }
            return existing
        }
        return entryRepository.saveAndFlush(newEntry(user, card, chosenVariant, quantity, condition))
    }

    /** Replaces both values, so leaving out the condition clears it. */
    @Transactional
    fun update(userId: Long, entryId: Long, quantity: Int, condition: CardCondition?): CollectionEntry {
        requireUsableQuantity(quantity)
        val entry = findOwned(userId, entryId)
        entry.quantity = quantity
        entry.condition = condition
        return entry
    }

    /** Returns the id of the card the entry was for. */
    @Transactional
    fun remove(userId: Long, entryId: Long): Long {
        val entry = findOwned(userId, entryId)
        val cardId = entry.card.id
        entryRepository.delete(entry)
        return cardId
    }

    /**
     * Marks a list of cards as owned: one copy of the default variant of every card that isn't in the collection
     * yet. Cards the customer already has stay as they are, so running it twice changes nothing.
     */
    @Transactional
    fun addAll(user: User, cardIds: List<Long>): BulkAddResult {
        val ids = cardIds.distinct()
        val cards = cardRepository.findAllById(ids)
        if (cards.size != ids.size) throw ApiException.validation("Niet alle kaarten bestaan.")

        val owned = entryRepository.findOwnedCardIds(user.id, ids).toSet()
        val new = cards.filter { it.id !in owned }
        entryRepository.saveAll(new.map { newEntry(user, it, CardVariants.default(it.variants), 1, null) })
        return BulkAddResult(added = new.size, skipped = ids.size - new.size)
    }

    private fun newEntry(user: User, card: Card, variant: String, quantity: Int, condition: CardCondition?) =
        CollectionEntry(
            user = user,
            card = card,
            variant = variant,
            quantity = quantity,
            condition = condition,
            addedAt = clock.instant(),
        )

    private fun findOwned(userId: Long, entryId: Long): CollectionEntry =
        entryRepository.findByIdAndUserId(entryId, userId)
            ?: throw ApiException.notFound("Deze kaart staat niet in je collectie.")

    private fun requireUsableQuantity(quantity: Int) {
        if (quantity !in 1..MAX_QUANTITY) {
            throw ApiException.validation("Kies een aantal van 1 tot en met $MAX_QUANTITY.")
        }
    }

    private companion object {
        const val MAX_QUANTITY = 9999
    }
}
