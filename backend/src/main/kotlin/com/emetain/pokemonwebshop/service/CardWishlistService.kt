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
}
