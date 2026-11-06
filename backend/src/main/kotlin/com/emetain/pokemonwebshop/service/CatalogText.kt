package com.emetain.pokemonwebshop.service

import java.text.Normalizer
import java.util.Locale

/** The forms of a card's name and number that the database searches and sorts on. */
object CatalogText {

    private const val SORT_DIGITS = 6

    private val ACCENTS = Regex("\\p{M}+")
    private val NOT_A_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val DIGITS = Regex("\\d+")

    /** Lowercase, no accents and punctuation as spaces, so "pokemon" finds "Pokémon" and "ho oh" finds "Ho-Oh". */
    fun nameSearch(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(ACCENTS, "")
            .lowercase(Locale.ROOT)
            .replace(NOT_A_WORD, " ")
            .trim()

    /** Pads every group of digits, so 2 sorts before 10 and `TG01` becomes `TG000001`. */
    fun sortKey(number: String): String =
        DIGITS.replace(number) { it.value.padStart(SORT_DIGITS, '0') }
}
