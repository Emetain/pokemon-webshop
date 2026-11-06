package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardCondition
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.CollectionEntry
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CollectionEntryRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CollectionServiceTest {

    private val entries = mock<CollectionEntryRepository> {
        on { saveAndFlush(any<CollectionEntry>()) } doAnswer { it.getArgument(0) }
    }
    private val cards = mock<CardRepository>()
    private val service = CollectionService(entries, cards, MutableClock())

    private val user = User(email = "a@example.com", passwordHash = "x", displayName = "Ash", createdAt = Instant.EPOCH, id = 1)
    private val set = CardSet(Game.POKEMON, "base1", "base1", "Base Set", "Base", id = 1)

    private fun card(id: Long = 10, vararg variants: String) = Card(
        game = Game.POKEMON,
        set = set,
        externalId = "c$id",
        number = "$id",
        name = "Card $id",
        nameSearch = "card $id",
        sortKey = "$id",
        variants = variants.toMutableSet(),
        id = id,
    ).also { whenever(cards.findById(id)) doReturn Optional.of(it) }

    private fun entry(card: Card, variant: String, quantity: Int, condition: CardCondition? = null) =
        CollectionEntry(user, card, variant, quantity, condition, Instant.EPOCH, id = 5)

    @Test
    fun `without a variant the most common finish of the card is used`() {
        card(10, CardVariants.REVERSE, CardVariants.HOLO)

        val entry = service.add(user, 10, variant = null, quantity = 1, condition = null)

        assertEquals(CardVariants.HOLO, entry.variant)
    }

    @Test
    fun `a variant the card doesn't have is refused`() {
        card(10, CardVariants.NORMAL)

        val exception = assertThrows<ApiException> { service.add(user, 10, CardVariants.REVERSE, 1, null) }

        assertEquals("validation_failed", exception.code)
    }

    @Test
    fun `an unknown card is not found`() {
        whenever(cards.findById(99)) doReturn Optional.empty()

        val exception = assertThrows<ApiException> { service.add(user, 99, null, 1, null) }

        assertEquals("not_found", exception.code)
    }

    @Test
    fun `adding a card you already have adds to the quantity`() {
        val card = card(10, CardVariants.NORMAL)
        val existing = entry(card, CardVariants.NORMAL, 2)
        whenever(entries.findForUpdate(1, 10, CardVariants.NORMAL)) doReturn existing

        val entry = service.add(user, 10, CardVariants.NORMAL, 3, null)

        assertEquals(5, entry.quantity)
        verify(entries, never()).saveAndFlush(any<CollectionEntry>())
    }

    @Test
    fun `adding more copies keeps the condition unless a new one is given`() {
        val card = card(10, CardVariants.NORMAL)
        val existing = entry(card, CardVariants.NORMAL, 1, CardCondition.GOOD)
        whenever(entries.findForUpdate(1, 10, CardVariants.NORMAL)) doReturn existing

        assertEquals(CardCondition.GOOD, service.add(user, 10, CardVariants.NORMAL, 1, null).condition)
        assertEquals(CardCondition.MINT, service.add(user, 10, CardVariants.NORMAL, 1, CardCondition.MINT).condition)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, 10000])
    fun `a quantity outside the limits is refused`(quantity: Int) {
        card(10, CardVariants.NORMAL)

        assertThrows<ApiException> { service.add(user, 10, null, quantity, null) }
    }

    @Test
    fun `adding can't push the quantity past the limit`() {
        val card = card(10, CardVariants.NORMAL)
        whenever(entries.findForUpdate(1, 10, CardVariants.NORMAL)) doReturn entry(card, CardVariants.NORMAL, 9999)

        assertThrows<ApiException> { service.add(user, 10, CardVariants.NORMAL, 1, null) }
    }

    @Test
    fun `updating replaces the quantity and the condition`() {
        val existing = entry(card(10, CardVariants.NORMAL), CardVariants.NORMAL, 2, CardCondition.GOOD)
        whenever(entries.findByIdAndUserId(5, 1)) doReturn existing

        val entry = service.update(1, 5, quantity = 7, condition = null)

        assertEquals(7, entry.quantity)
        assertNull(entry.condition)
    }

    @Test
    fun `an entry of someone else counts as not found`() {
        whenever(entries.findByIdAndUserId(5, 1)) doReturn null

        assertEquals("not_found", assertThrows<ApiException> { service.update(1, 5, 1, null) }.code)
        assertEquals("not_found", assertThrows<ApiException> { service.remove(1, 5) }.code)
    }

    @Test
    fun `bulk add skips cards the customer already has, in any variant`() {
        val owned = card(10, CardVariants.NORMAL, CardVariants.REVERSE)
        val new = card(11, CardVariants.HOLO)
        whenever(cards.findAllById(listOf(10L, 11L))) doReturn listOf(owned, new)
        whenever(entries.findOwnedCardIds(1, listOf(10L, 11L))) doReturn listOf(10L)

        val result = service.addAll(user, listOf(10, 11, 10))

        assertEquals(BulkAddResult(added = 1, skipped = 1), result)
    }

    @Test
    fun `bulk add refuses a list with a card that doesn't exist`() {
        val known = card(10, CardVariants.NORMAL)
        whenever(cards.findAllById(listOf(10L, 99L))) doReturn listOf(known)

        assertEquals("validation_failed", assertThrows<ApiException> { service.addAll(user, listOf(10, 99)) }.code)
    }
}
