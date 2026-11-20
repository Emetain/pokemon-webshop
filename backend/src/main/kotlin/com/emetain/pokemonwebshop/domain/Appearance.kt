package com.emetain.pokemonwebshop.domain

/** How the website looks for a customer (phase 12). Dark mode is free for everyone: the owner's choice, 2026-11-20. */
enum class ColorMode { LIGHT, DARK, SYSTEM }

/**
 * The colour themes of the website, built on the tokens in `main.css` (phase 12). Poketopia's own blue is for everyone;
 * the others are Premium. The Android app of phase 13 uses the same ones.
 */
enum class ColorTheme(val premium: Boolean) {
    POKETOPIA(false),
    FIRE(true),
    GRASS(true),
    ELECTRIC(true),
    PSYCHIC(true),
}
