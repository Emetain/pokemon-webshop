package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.CardCondition
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Game
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * The Dutch names of variants and conditions, the same as `catalog.variants` and `collection.conditions` in the
 * frontend's `nl.js`, for mails and exports that the backend writes (phase 12).
 */
object CardLabels {

    fun variant(variant: String): String = when (variant) {
        CardVariants.NORMAL -> "Normaal"
        CardVariants.HOLO -> "Holo"
        CardVariants.REVERSE -> "Reverse holo"
        CardVariants.FIRST_EDITION -> "1st Edition"
        CardVariants.W_PROMO -> "W Promo"
        CardVariants.FOIL -> "Foil"
        CardVariants.ETCHED -> "Etched foil"
        else -> variant
    }

    fun game(game: Game): String = when (game) {
        Game.POKEMON -> "Pokémon"
        Game.LORCANA -> "Disney Lorcana"
        Game.MTG -> "Magic: The Gathering"
        Game.ONE_PIECE -> "One Piece"
        Game.RIFTBOUND -> "Riftbound"
    }

    fun condition(condition: CardCondition?): String = when (condition) {
        null -> ""
        CardCondition.MINT -> "Mint"
        CardCondition.NEAR_MINT -> "Near mint"
        CardCondition.EXCELLENT -> "Excellent"
        CardCondition.GOOD -> "Good"
        CardCondition.LIGHT_PLAYED -> "Light played"
        CardCondition.PLAYED -> "Played"
        CardCondition.POOR -> "Poor"
    }

    /** "€ 12,50" or "US$ 12,50" in Dutch notation, with a plain space. */
    @Synchronized
    fun money(cents: Long, currency: Currency): String =
        formats.getOrPut(currency) {
            NumberFormat.getCurrencyInstance(DUTCH).apply { this.currency = java.util.Currency.getInstance(currency.name) }
        }.format(BigDecimal.valueOf(cents, 2)).replace(' ', ' ')

    /** "12,50", for a spreadsheet column whose currency is in the header. */
    @Synchronized
    fun amount(cents: Long): String = plain.format(BigDecimal.valueOf(cents, 2))

    private val DUTCH: Locale = Locale.forLanguageTag("nl-NL")
    private val formats = mutableMapOf<Currency, NumberFormat>()
    private val plain = NumberFormat.getNumberInstance(DUTCH).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = false
    }
}
