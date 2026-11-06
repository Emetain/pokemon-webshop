package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** One slot of the Pokédex. */
@Entity
@Table(name = "pokemon_species")
class PokemonSpecies(
    @Id
    val dexNumber: Int,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false)
    var generation: Int,
)
