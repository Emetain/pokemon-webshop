package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CollectionEntry
import com.emetain.pokemonwebshop.repository.CollectionEntryRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import org.springframework.stereotype.Service

/**
 * A card that lost finishes at its source, for example an ex card of a new set that TCGdex first listed as "normal"
 * and later as "holo" (2026-11-20). Collection entries and shop singles of a finish that's gone move to the card's main
 * finish, so nobody loses a card from their collection and no single points at a finish that doesn't exist. Copies of
 * one customer add up. The saved price history of the old finish stays as it was.
 */
@Service
class CardVariantMover(
    private val collectionEntryRepository: CollectionEntryRepository,
    private val productRepository: ProductRepository,
) {

    /** Runs inside the import's transaction. [target] must be one of the card's finishes now. */
    fun moveRemoved(card: Card, removed: Set<String>, target: String) {
        if (card.id == 0L || removed.isEmpty() || target in removed) return

        collectionEntryRepository.findAllByCardIdAndVariantIn(card.id, removed)
            .groupBy { it.user.id }
            .forEach { (userId, entries) ->
                val quantity = entries.sumOf { it.quantity }
                val condition = entries.firstNotNullOfOrNull { it.condition }
                val kept = collectionEntryRepository.findByUserIdAndCardIdAndVariant(userId, card.id, target)
                if (kept != null) {
                    kept.quantity += quantity
                    if (kept.condition == null) kept.condition = condition
                } else {
                    collectionEntryRepository.save(
                        CollectionEntry(
                            user = entries.first().user,
                            card = card,
                            variant = target,
                            quantity = quantity,
                            condition = condition,
                            addedAt = entries.minOf { it.addedAt },
                        ),
                    )
                }
                collectionEntryRepository.deleteAll(entries)
            }

        productRepository.findAllByCardIdAndCardVariantIn(card.id, removed).forEach { it.cardVariant = target }
    }
}
