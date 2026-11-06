package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.CardWishlistEntry
import org.springframework.data.jpa.repository.JpaRepository

interface CardWishlistEntryRepository : JpaRepository<CardWishlistEntry, Long> {
    fun existsByUserIdAndCardId(userId: Long, cardId: Long): Boolean

    fun findByUserIdAndCardId(userId: Long, cardId: Long): CardWishlistEntry?
}
