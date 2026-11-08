package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.Game
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShopSetRulesTest {

    private val temporalForces = LocalDate.of(2024, 3, 22)

    private val rules = ShopSetRules(
        testProperties().let {
            it.copy(
                shop = it.shop.copy(
                    inPrintFrom = mapOf(Game.POKEMON to temporalForces),
                    popularSets = mapOf(Game.POKEMON to listOf("me02.5", "sv08.5")),
                ),
            )
        },
        mock(),
        mock(),
    )

    private fun set(code: String, releaseDate: LocalDate?, game: Game = Game.POKEMON) =
        CardSet(game = game, externalId = code, code = code, name = code, series = "Scarlet & Violet", releaseDate = releaseDate)

    @Test
    fun `a set released before the date is out of print, from the date on it is in print`() {
        assertFalse(rules.inPrint(Game.POKEMON, LocalDate.of(2024, 1, 26)))
        assertTrue(rules.inPrint(Game.POKEMON, temporalForces))
        assertTrue(rules.inPrint(Game.POKEMON, LocalDate.of(2026, 9, 16)))
    }

    @Test
    fun `a set without a release date, or of a game without a date, stays in print`() {
        assertTrue(rules.inPrint(Game.POKEMON, null))
        assertTrue(rules.inPrint(Game.MTG, LocalDate.of(1993, 8, 5)))
    }

    @Test
    fun `popular sets get their place on the list, others none`() {
        assertEquals(0, rules.popularRank(Game.POKEMON, "me02.5"))
        assertEquals(1, rules.popularRank(Game.POKEMON, "sv08.5"))
        assertNull(rules.popularRank(Game.POKEMON, "sv01"))
        assertNull(rules.popularRank(Game.MTG, "me02.5"))
    }

    @Test
    fun `applying the rules says whether the set changed`() {
        val paldeanFates = set("sv04.5", LocalDate.of(2024, 1, 26))
        assertTrue(rules.apply(paldeanFates))
        assertFalse(paldeanFates.inPrint)
        assertFalse(rules.apply(paldeanFates))

        val ascendedHeroes = set("me02.5", LocalDate.of(2026, 1, 30))
        assertTrue(rules.apply(ascendedHeroes))
        assertTrue(ascendedHeroes.inPrint)
        assertEquals(0, ascendedHeroes.popularRank)
    }
}
