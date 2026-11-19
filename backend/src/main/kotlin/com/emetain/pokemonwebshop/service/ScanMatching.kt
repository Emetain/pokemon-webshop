package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.Game

/** What the model read on a card photo (phase 11). Anything it couldn't read is null. */
data class ScanReading(
    val game: Game?,
    val name: String?,
    val setCode: String?,
    val setName: String?,
    /** As printed before a slash: "084" from "084/182", "OP01-001", "377". */
    val number: String?,
) {
    val readable: Boolean get() = !name.isNullOrBlank() || !number.isNullOrBlank()
}

data class ScanCandidate(val card: Card, val score: Int)

/**
 * Finds the cards a reading can be (phase 11). Points for the name, the collector number, the set code and the set
 * name; one card far ahead of the rest is a match, otherwise the customer picks from a short list. Pure, so the rules
 * have their own tests.
 */
object ScanMatching {

    const val MAX_CANDIDATES = 5

    /** Name and number both right, or one of them with the set: sure enough to show one card. */
    const val SURE = 8

    private const val NAME_EXACT = 4
    private const val NAME_PARTLY = 2
    private const val NAME_WORDS = 1
    private const val NUMBER = 4
    private const val SET_CODE = 3
    private const val SET_NAME_EXACT = 2
    private const val SET_NAME_PARTLY = 1
    private const val MINIMUM = 4

    fun rank(reading: ScanReading, cards: Collection<Card>): List<ScanCandidate> {
        val name = reading.name?.let(CatalogText::nameSearch)?.takeIf { it.isNotBlank() }
        val number = reading.number?.trim()?.takeIf { it.isNotBlank() }?.let(CatalogText::sortKey)
        val setCode = reading.setCode?.let(::code)?.takeIf { it.isNotBlank() }
        val setName = reading.setName?.let(CatalogText::nameSearch)?.takeIf { it.isNotBlank() }

        return cards.distinctBy { it.id }
            .map { card ->
                val nameScore = name?.let { nameScore(it, card.nameSearch) } ?: 0
                val numberScore = if (number != null && card.sortKey.equals(number, ignoreCase = true)) NUMBER else 0
                val setScore = (if (setCode != null && code(card.set.code) == setCode) SET_CODE else 0) +
                    (setName?.let { setNameScore(it, CatalogText.nameSearch(card.set.name)) } ?: 0)
                // A number alone is in every set: it needs the name or the set with it.
                val score = if (nameScore == 0 && setScore == 0) 0 else nameScore + numberScore + setScore
                ScanCandidate(card, score)
            }
            .filter { it.score >= MINIMUM }
            .sortedWith(
                compareByDescending<ScanCandidate> { it.score }
                    .thenByDescending { it.card.set.releaseDate }
                    .thenBy { it.card.sortKey },
            )
            .take(MAX_CANDIDATES)
    }

    /** One card when it is sure and clearly ahead of the next one. */
    fun match(candidates: List<ScanCandidate>): ScanCandidate? {
        val first = candidates.firstOrNull() ?: return null
        val second = candidates.getOrNull(1)
        return first.takeIf { it.score >= SURE && (second == null || second.score < first.score) }
    }

    private fun nameScore(read: String, card: String): Int = when {
        read == card -> NAME_EXACT
        card.startsWith("$read ") || read.startsWith("$card ") -> NAME_PARTLY
        overlap(read, card) -> NAME_WORDS
        else -> 0
    }

    private fun setNameScore(read: String, set: String): Int = when {
        read == set -> SET_NAME_EXACT
        set.contains(read) || read.contains(set) -> SET_NAME_PARTLY
        else -> 0
    }

    private fun overlap(a: String, b: String): Boolean {
        val words = a.split(' ').filter { it.length > 2 }.toSet()
        if (words.isEmpty()) return false
        return b.split(' ').count { it in words } * 2 >= words.size
    }

    /** "OP-01", "op01" and "OP01" are the same set code. */
    private fun code(text: String): String = text.uppercase().filter { it.isLetterOrDigit() }
}
