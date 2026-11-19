package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.Game
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanMatchingTest {

    private var nextId = 1L

    private fun set(code: String, name: String, released: LocalDate = LocalDate.of(2026, 10, 1), game: Game = Game.POKEMON) =
        CardSet(game = game, externalId = code, code = code, name = name, series = "S").apply { releaseDate = released }

    private fun card(set: CardSet, number: String, name: String) = Card(
        game = set.game,
        set = set,
        externalId = "${set.code}-$number",
        number = number,
        name = name,
        nameSearch = CatalogText.nameSearch(name),
        sortKey = CatalogText.sortKey(number),
        id = nextId++,
    )

    private val deltaReign = set("me06", "Delta Reign")
    private val older = set("sv01", "Scarlet & Violet", LocalDate.of(2023, 3, 31))
    private val rayquaza = card(deltaReign, "138", "Mega Rayquaza ex")
    private val otherRayquaza = card(deltaReign, "084", "Mega Rayquaza ex")
    private val stranger = card(older, "138", "Glimmora")

    @Test
    fun `name and number make a sure match`() {
        val candidates = ScanMatching.rank(ScanReading(Game.POKEMON, "Mega Rayquaza ex", null, null, "138"), listOf(stranger, otherRayquaza, rayquaza))

        assertEquals(rayquaza, ScanMatching.match(candidates)?.card)
        assertEquals(listOf(rayquaza, otherRayquaza), candidates.map { it.card })
    }

    @Test
    fun `a number alone doesn't find a card`() {
        assertTrue(ScanMatching.rank(ScanReading(Game.POKEMON, null, null, null, "138"), listOf(stranger, rayquaza)).isEmpty())
    }

    @Test
    fun `a name without a number gives a list to choose from`() {
        val candidates = ScanMatching.rank(ScanReading(Game.POKEMON, "Mega Rayquaza ex", null, null, null), listOf(otherRayquaza, rayquaza))

        assertEquals(2, candidates.size)
        assertNull(ScanMatching.match(candidates))
    }

    @Test
    fun `the set code and number find a Magic card with a slightly different name`() {
        val dominaria = set("dmu", "Dominaria United", game = Game.MTG)
        val card = card(dominaria, "107", "Sheoldred, the Apocalypse")

        val candidates = ScanMatching.rank(ScanReading(Game.MTG, "Sheoldred", "DMU", null, "107"), listOf(card))

        assertEquals(card, ScanMatching.match(candidates)?.card)
    }

    @Test
    fun `one piece numbers and set codes compare without punctuation`() {
        val romance = set("OP-01", "Romance Dawn", game = Game.ONE_PIECE)
        val card = card(romance, "OP01-001", "Roronoa Zoro")

        val candidates = ScanMatching.rank(ScanReading(Game.ONE_PIECE, "Roronoa Zoro", "OP01", null, "OP01-001"), listOf(card))

        assertEquals(card, ScanMatching.match(candidates)?.card)
    }

    @Test
    fun `leading zeros in the number don't matter`() {
        val card = card(older, "4", "Charizard")

        assertEquals(card, ScanMatching.match(ScanMatching.rank(ScanReading(null, "Charizard", null, null, "004"), listOf(card)))?.card)
    }
}
