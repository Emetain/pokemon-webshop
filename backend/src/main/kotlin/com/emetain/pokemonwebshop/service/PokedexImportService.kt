package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.PokemonSpecies
import com.emetain.pokemonwebshop.repository.PokemonSpeciesRepository
import com.emetain.pokemonwebshop.service.provider.PokeApiProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Imports the list of Pokémon. A species never changes, so only new ones are requested: the first run makes one
 * request per species and later runs make a single one. That is also what PokéAPI's fair use policy asks for.
 */
@Service
class PokedexImportService(
    private val provider: PokeApiProvider,
    private val speciesRepository: PokemonSpeciesRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Saves each species right away, so a run that fails halfway continues there next time. */
    fun refresh() {
        val known = speciesRepository.findAllDexNumbers().toSet()
        val missing = provider.speciesNumbers().filterNot(known::contains)

        for (number in missing) {
            val species = provider.species(number)
            speciesRepository.save(PokemonSpecies(species.dexNumber, species.name, species.generation))
        }
        if (missing.isNotEmpty()) log.info("Imported {} Pokémon species", missing.size)
    }
}
