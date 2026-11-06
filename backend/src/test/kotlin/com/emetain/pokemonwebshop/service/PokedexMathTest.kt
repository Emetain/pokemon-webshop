package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.PokemonSpecies
import com.emetain.pokemonwebshop.repository.OwnedDexCard
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PokedexMathTest {

    private val species = listOf(
        PokemonSpecies(1, "Bulbasaur", 1),
        PokemonSpecies(25, "Pikachu", 1),
        PokemonSpecies(152, "Chikorita", 2),
    )

    private fun card(dexNumber: Int, cardId: Long = 1, image: String? = "https://example.com/$cardId.webp", price: Long? = null) =
        OwnedDexCard(dexNumber, cardId, Game.POKEMON, "base1", image, price)

    @Test
    fun `an empty collection fills no slots`() {
        val pokedex = PokedexCalculator.pokedex(species, emptyList())

        assertEquals(Progress(0, 3), pokedex.progress)
        assertFalse(pokedex.slots.any { it.filled })
    }

    @Test
    fun `one card fills one slot, however many copies there are`() {
        val pokedex = PokedexCalculator.pokedex(species, listOf(card(25), card(25, cardId = 2)))

        assertEquals(Progress(1, 3), pokedex.progress)
        assertTrue(pokedex.slots.single { it.dexNumber == 25 }.filled)
        assertEquals(2, pokedex.slots.single { it.dexNumber == 25 }.ownedCards)
    }

    @Test
    fun `a card with two Pokémon fills both slots`() {
        val pokedex = PokedexCalculator.pokedex(species, listOf(card(1), card(25)))

        assertEquals(Progress(2, 3), pokedex.progress)
    }

    @Test
    fun `progress is counted per generation`() {
        val pokedex = PokedexCalculator.pokedex(species, listOf(card(25)))

        assertEquals(
            listOf(GenerationProgress(1, Progress(1, 2)), GenerationProgress(2, Progress(0, 1))),
            pokedex.generations,
        )
    }

    @Test
    fun `a number without a species is ignored`() {
        val pokedex = PokedexCalculator.pokedex(species, listOf(card(9999)))

        assertEquals(Progress(0, 3), pokedex.progress)
    }

    @Test
    fun `a slot shows the most valuable owned card`() {
        val owned = listOf(card(25, cardId = 1, price = 100), card(25, cardId = 2, price = 900), card(25, cardId = 3))

        val slot = PokedexCalculator.pokedex(species, owned).slots.single { it.dexNumber == 25 }

        assertEquals(2, slot.card?.cardId)
    }

    @Test
    fun `a card without an image fills the slot but doesn't show`() {
        val noImage = card(25, cardId = 1, image = null, price = 900)

        val onlyNoImage = PokedexCalculator.pokedex(species, listOf(noImage)).slots.single { it.dexNumber == 25 }
        val withImage = PokedexCalculator.pokedex(species, listOf(noImage, card(25, cardId = 2, price = 1)))
            .slots.single { it.dexNumber == 25 }

        assertTrue(onlyNoImage.filled)
        assertNull(onlyNoImage.card)
        assertEquals(2, withImage.card?.cardId)
    }
}
