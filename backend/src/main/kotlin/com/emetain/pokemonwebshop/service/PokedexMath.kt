package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.PokemonSpecies
import com.emetain.pokemonwebshop.repository.OwnedDexCard

data class PokedexSlot(
    val dexNumber: Int,
    val name: String,
    val generation: Int,
    val ownedCards: Int,
    /** The card shown in the slot. Null for an empty slot, or when no owned card has an image. */
    val card: OwnedDexCard?,
) {
    val filled get() = ownedCards > 0
}

data class GenerationProgress(val generation: Int, val progress: Progress)

data class Pokedex(
    val progress: Progress,
    val generations: List<GenerationProgress>,
    val slots: List<PokedexSlot>,
)

object PokedexCalculator {

    /** A slot is filled when the customer owns at least one card with that Pokédex number. */
    fun pokedex(species: List<PokemonSpecies>, owned: List<OwnedDexCard>): Pokedex {
        val cardsByNumber = owned.groupBy { it.dexNumber }
        val slots = species.map { slot(it, cardsByNumber[it.dexNumber].orEmpty()) }
        return Pokedex(
            progress = progress(slots),
            generations = slots.groupBy { it.generation }
                .map { (generation, inGeneration) -> GenerationProgress(generation, progress(inGeneration)) }
                .sortedBy { it.generation },
            slots = slots,
        )
    }

    private fun slot(species: PokemonSpecies, cards: List<OwnedDexCard>) = PokedexSlot(
        dexNumber = species.dexNumber,
        name = species.name,
        generation = species.generation,
        ownedCards = cards.distinctBy { it.cardId }.size,
        card = cards.filter { it.imageUrl != null }.minWithOrNull(BEST_CARD_FIRST),
    )

    private fun progress(slots: List<PokedexSlot>) = Progress(slots.count { it.filled }.toLong(), slots.size.toLong())

    // The most valuable card, and the id so the slot doesn't change between visits.
    private val BEST_CARD_FIRST = compareByDescending<OwnedDexCard> { it.sortPriceCents ?: -1L }.thenBy { it.cardId }
}
