package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.PokemonSpeciesRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PokedexService(
    private val speciesRepository: PokemonSpeciesRepository,
    private val cardRepository: CardRepository,
) {

    /** Without a user every slot is empty, which is what a visitor who isn't logged in sees. */
    @Transactional(readOnly = true)
    fun pokedex(userId: Long?): Pokedex = PokedexCalculator.pokedex(
        species = speciesRepository.findAllByOrderByDexNumber(),
        owned = userId?.let(cardRepository::findOwnedDexCards).orEmpty(),
    )
}
