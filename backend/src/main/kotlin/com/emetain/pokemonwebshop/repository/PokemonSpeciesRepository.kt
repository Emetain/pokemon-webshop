package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.PokemonSpecies
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PokemonSpeciesRepository : JpaRepository<PokemonSpecies, Int> {
    fun findAllByOrderByDexNumber(): List<PokemonSpecies>

    @Query("select s.dexNumber from PokemonSpecies s")
    fun findAllDexNumbers(): List<Int>
}
