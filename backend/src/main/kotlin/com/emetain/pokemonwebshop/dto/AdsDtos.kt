package com.emetain.pokemonwebshop.dto

/**
 * What the frontend needs to show ads. `enabled` false: no ads, and nothing from Google is loaded. `slots` maps a
 * place in the site (`catalog`, `content`) to the id of its ad unit; a place without one shows nothing.
 */
data class AdsDto(
    val enabled: Boolean,
    val client: String? = null,
    val slots: Map<String, String> = emptyMap(),
)
