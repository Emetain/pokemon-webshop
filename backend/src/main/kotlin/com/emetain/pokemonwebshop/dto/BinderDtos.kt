package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.BinderSource
import com.emetain.pokemonwebshop.domain.Card

data class BinderCardDto(
    val id: Long,
    val game: String,
    val setCode: String,
    val name: String,
    val imageUrl: String?,
) {
    companion object {
        /** Reads the set, so call this while it can still be loaded. */
        fun from(card: Card) = BinderCardDto(card.id, card.game.slug, card.set.code, card.name, card.imageSmallUrl)
    }
}

/** A pocket of the binder. In the Pokédex source a pocket can be empty, and then only has a number and a name. */
data class BinderSlotDto(
    val card: BinderCardDto?,
    val dexNumber: Int? = null,
    val name: String? = null,
)

data class BinderPageDto(
    val source: BinderSource,
    /** True when the chosen source has nothing to show yet and popular cards took its place. */
    val fallback: Boolean,
    val page: Int,
    val totalPages: Int,
    val slots: List<BinderSlotDto>,
)
