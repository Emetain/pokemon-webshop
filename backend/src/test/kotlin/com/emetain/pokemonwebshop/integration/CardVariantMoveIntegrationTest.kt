package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.repository.CollectionEntryRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals

/**
 * A card that loses a finish at its source keeps its owners (2026-11-20): TCGdex first lists every card of a new set
 * as "normal", and later an ex card as "holo" only.
 */
class CardVariantMoveIntegrationTest : CollectionTestSupport() {

    @Autowired
    private lateinit var collectionEntryRepository: CollectionEntryRepository

    @Autowired
    private lateinit var transaction: TransactionTemplate

    private fun entries(cardId: Long) = transaction.execute {
        collectionEntryRepository.findAll()
            .filter { it.card.id == cardId }
            .associate { it.variant to it.quantity }
    }

    @Test
    fun `copies of a finish that's gone move to the card's main finish`() {
        val id = uniqueCode()
        val code = importCards(listOf(providerCard(id, rarity = "Double rare", variants = setOf(CardVariants.NORMAL))))
        val cardId = card(id).id
        add(loggedInUser(), cardId, CardVariants.NORMAL, quantity = 2)

        importCards(listOf(providerCard(id, rarity = "Double rare", variants = setOf(CardVariants.HOLO))), code = code)

        assertEquals(mapOf(CardVariants.HOLO to 2), entries(cardId))
    }

    @Test
    fun `a finish that stays keeps its copies, and copies of one customer add up`() {
        val id = uniqueCode()
        val code = importCards(
            listOf(providerCard(id, rarity = "Rare", variants = setOf(CardVariants.NORMAL, CardVariants.REVERSE))),
        )
        val cardId = card(id).id
        val session = loggedInUser()
        add(session, cardId, CardVariants.NORMAL, quantity = 1)
        add(session, cardId, CardVariants.REVERSE, quantity = 3)

        // A rare of a modern set is holo and reverse holo: "normal" goes, "reverse" stays.
        importCards(
            listOf(providerCard(id, rarity = "Rare", variants = setOf(CardVariants.HOLO, CardVariants.REVERSE))),
            code = code,
        )

        assertEquals(mapOf(CardVariants.HOLO to 1, CardVariants.REVERSE to 3), entries(cardId))
    }
}
