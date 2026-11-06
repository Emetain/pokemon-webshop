package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.PokemonSpecies
import com.emetain.pokemonwebshop.repository.PokemonSpeciesRepository
import com.emetain.pokemonwebshop.service.provider.PokeApiProvider
import com.emetain.pokemonwebshop.service.provider.ProviderSpecies
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class PokedexImportServiceTest {

    private val provider = mock<PokeApiProvider>()
    private val repository = mock<PokemonSpeciesRepository>()
    private val service = PokedexImportService(provider, repository)

    @Test
    fun `only asks for species it doesn't have yet`() {
        whenever(repository.findAllDexNumbers()) doReturn listOf(1, 2)
        whenever(provider.speciesNumbers()) doReturn listOf(1, 2, 3)
        whenever(provider.species(3)) doReturn ProviderSpecies(3, "Venusaur", 1)

        service.refresh()

        verify(provider, never()).species(1)
        verify(provider, never()).species(2)
        val saved = argumentCaptor<PokemonSpecies>().apply { verify(repository).save(capture()) }.firstValue
        assertEquals(3, saved.dexNumber)
        assertEquals("Venusaur", saved.name)
        assertEquals(1, saved.generation)
    }

    @Test
    fun `a run without new species makes one request and saves nothing`() {
        whenever(repository.findAllDexNumbers()) doReturn listOf(1)
        whenever(provider.speciesNumbers()) doReturn listOf(1)

        service.refresh()

        verify(provider, times(1)).speciesNumbers()
        verify(provider, never()).species(any())
        verify(repository, never()).save(any<PokemonSpecies>())
    }

    @Test
    fun `keeps what it saved when a species fails, so the next run continues there`() {
        whenever(repository.findAllDexNumbers()) doReturn emptyList()
        whenever(provider.speciesNumbers()) doReturn listOf(1, 2)
        whenever(provider.species(1)) doReturn ProviderSpecies(1, "Bulbasaur", 1)
        whenever(provider.species(2)) doThrow RuntimeException("PokéAPI is down")

        assertThrows<RuntimeException> { service.refresh() }

        verify(repository, times(1)).save(any<PokemonSpecies>())
    }
}
