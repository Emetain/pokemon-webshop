package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Game

/** A count per set. */
interface SetCount {
    val setId: Long
    val count: Long
}

/** The number of different card numbers in a set. */
interface SetNumbers {
    val setId: Long
    val game: Game
    val count: Long
}

/** One [com.emetain.pokemonwebshop.domain.CollectionEntry] with the market price of the owned variant. */
data class OwnedLine @JvmOverloads constructor(
    val cardId: Long,
    val game: Game,
    val setId: Long,
    val variant: String,
    val quantity: Int,
    val trendCents: Long? = null,
    val currency: Currency? = null,
)

/** The market price of one variant of an owned card. */
data class OwnedPrice(val cardId: Long, val variant: String, val trendCents: Long?, val currency: Currency)

/** A card in a collection, once for every Pokémon on it. */
data class OwnedDexCard(
    val dexNumber: Int,
    val cardId: Long,
    val game: Game,
    val setCode: String,
    val imageUrl: String?,
    val sortPriceCents: Long?,
)
