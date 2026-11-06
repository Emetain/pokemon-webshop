package com.emetain.pokemonwebshop.service.provider

import com.fasterxml.jackson.databind.JsonNode

data class ProviderSpecies(val dexNumber: Int, val name: String, val generation: Int)

object PokeApiMapper {

    /** The list only links to each species, with the number at the end of the link. */
    fun speciesNumbers(list: JsonNode): List<Int> =
        list.path("results").map { numberAtEndOf(it.requireText("url")) }

    fun species(species: JsonNode): ProviderSpecies {
        // The `name` field is a slug such as `mr-mime`. The display names are per language.
        val english = species.path("names").firstOrNull { it.path("language").text("name") == ENGLISH }
        return ProviderSpecies(
            dexNumber = species.path("id").asInt(),
            name = english?.text("name") ?: species.requireText("name"),
            generation = numberAtEndOf(species.path("generation").requireText("url")),
        )
    }

    private fun numberAtEndOf(url: String): Int =
        requireNotNull(url.trimEnd('/').substringAfterLast('/').toIntOrNull()) { "No number at the end of $url" }

    private const val ENGLISH = "en"
}
