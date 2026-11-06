package com.emetain.pokemonwebshop.service.provider

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class PokeApiMapperTest {

    @Test
    fun `reads the numbers from the links in the list`() {
        assertEquals(listOf(1, 2, 3), PokeApiMapper.speciesNumbers(fixture("pokeapi-species-list.json")))
    }

    @Test
    fun `uses the display name and not the slug`() {
        val species = PokeApiMapper.species(fixture("pokeapi-species-122.json"))

        assertEquals(ProviderSpecies(dexNumber = 122, name = "Mr. Mime", generation = 1), species)
    }

    @Test
    fun `falls back on the slug when there is no english name`() {
        val species = PokeApiMapper.species(
            json(
                """{"id": 1025, "name": "pecharunt", "names": [],
                   "generation": {"name": "generation-ix", "url": "https://pokeapi.co/api/v2/generation/9/"}}""",
            ),
        )

        assertEquals(ProviderSpecies(1025, "pecharunt", 9), species)
    }

    @Test
    fun `a link without a number is an error and not a wrong number`() {
        assertThrows<IllegalArgumentException> {
            PokeApiMapper.speciesNumbers(json("""{"results": [{"url": "https://pokeapi.co/api/v2/pokemon-species/"}]}"""))
        }
    }
}
