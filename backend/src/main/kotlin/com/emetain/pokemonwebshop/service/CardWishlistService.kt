package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.CardWishlistEntry
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardWishlistEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** Adding a card twice or removing it twice is fine, so a double click can't fail. */
@Service
class CardWishlistService(
    private val wishlistRepository: CardWishlistEntryRepository,
    private val cardRepository: CardRepository,
    private val clock: Clock,
) {

    @Transactional
    fun add(user: User, cardId: Long) {
        if (wishlistRepository.existsByUserIdAndCardId(user.id, cardId)) return
        val card = cardRepository.findById(cardId).orElseThrow { ApiException.notFound("We konden deze kaart niet vinden.") }
        wishlistRepository.saveAndFlush(CardWishlistEntry(user, card, clock.instant()))
    }

    @Transactional
    fun remove(userId: Long, cardId: Long) {
        wishlistRepository.findByUserIdAndCardId(userId, cardId)?.let(wishlistRepository::delete)
    }

    @Transactional(readOnly = true)
    fun contains(userId: Long, cardId: Long): Boolean = wishlistRepository.existsByUserIdAndCardId(userId, cardId)

    @Transactional(readOnly = true)
    fun entry(userId: Long, cardId: Long): CardWishlistEntry? = wishlistRepository.findByUserIdAndCardId(userId, cardId)

    /**
     * The most the customer wants to pay for a card in the shop (2026-11-23), in euro cents; null takes it away. A card
     * that isn't on the wishlist yet goes on it: a maximum only means something for a card you want.
     */
    @Transactional
    fun setMaxPrice(user: User, cardId: Long, maxPriceCents: Long?) {
        if (maxPriceCents != null && maxPriceCents !in 1..MAX_PRICE_CENTS) {
            throw ApiException.validation("Kies een maximum tussen € 0,01 en € 100.000.")
        }
        val entry = wishlistRepository.findByUserIdAndCardId(user.id, cardId) ?: run {
            val card = cardRepository.findById(cardId).orElseThrow { ApiException.notFound("We konden deze kaart niet vinden.") }
            wishlistRepository.saveAndFlush(CardWishlistEntry(user, card, clock.instant()))
        }
        entry.maxPriceCents = maxPriceCents
    }

    private companion object {
        const val MAX_PRICE_CENTS = 10_000_000L
    }
}
