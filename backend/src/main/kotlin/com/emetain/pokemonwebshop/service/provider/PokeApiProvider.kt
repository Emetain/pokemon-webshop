package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.config.AppProperties
import org.springframework.stereotype.Component

/** The list of all Pokémon from PokéAPI. Its list endpoint has no English names, so each species has its own call. */
@Component
class PokeApiProvider(
    private val http: CatalogHttpClient,
    properties: AppProperties,
) {

    private val source = properties.catalog.pokeapi

    fun speciesNumbers(): List<Int> =
        PokeApiMapper.speciesNumbers(http.getJson("${source.baseUrl}/pokemon-species?limit=$LIST_LIMIT", source.requestDelay))

    fun species(dexNumber: Int): ProviderSpecies =
        PokeApiMapper.species(http.getJson("${source.baseUrl}/pokemon-species/$dexNumber", source.requestDelay))

    private companion object {
        // Comfortably more than all Pokémon, so the whole list fits in one request.
        const val LIST_LIMIT = 5000
    }
}
