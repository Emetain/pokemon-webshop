package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.PokemonSpecies
import com.emetain.pokemonwebshop.service.GenerationProgress
import com.emetain.pokemonwebshop.service.Pokedex
import com.emetain.pokemonwebshop.service.PokedexSlot

data class PokedexSlotDto(
    val dexNumber: Int,
    val name: String,
    val generation: Int,
    val filled: Boolean,
    /** The image of one of the owned cards, when one has an image. */
    val imageUrl: String?,
) {
    companion object {
        fun from(slot: PokedexSlot) = PokedexSlotDto(
            dexNumber = slot.dexNumber,
            name = slot.name,
            generation = slot.generation,
            filled = slot.filled,
            imageUrl = slot.card?.imageUrl,
        )
    }
}

data class GenerationProgressDto(val generation: Int, val progress: ProgressDto) {
    companion object {
        fun from(generation: GenerationProgress) =
            GenerationProgressDto(generation.generation, ProgressDto.from(generation.progress))
    }
}

data class PokedexDto(
    val progress: ProgressDto,
    val generations: List<GenerationProgressDto>,
    val slots: List<PokedexSlotDto>,
) {
    companion object {
        fun from(pokedex: Pokedex) = PokedexDto(
            progress = ProgressDto.from(pokedex.progress),
            generations = pokedex.generations.map(GenerationProgressDto::from),
            slots = pokedex.slots.map(PokedexSlotDto::from),
        )
    }
}

data class PokemonLinkDto(val dexNumber: Int, val name: String) {
    companion object {
        fun from(species: PokemonSpecies) = PokemonLinkDto(species.dexNumber, species.name)
    }
}

/** One Pokémon with the filter options of its cards. */
data class PokedexEntryDto(
    val dexNumber: Int,
    val name: String,
    val generation: Int,
    val totalCards: Long,
    /** Null for a visitor who isn't logged in. */
    val ownedCards: Long?,
    val previous: PokemonLinkDto?,
    val next: PokemonLinkDto?,
    val rarities: List<String>,
    val variants: List<String>,
    val colors: List<String>,
)
