package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.time.LocalDate

/** Where a release suggestion came from: the card catalog's own sync (Scryfall and the others) or TCGplayer. */
enum class ReleaseSource { CATALOG, TCGPLAYER }

/**
 * An upcoming set that the card sources or TCGplayer already know. The admin adds it to the release calendar, or
 * dismisses it so it doesn't come back. One per game and name.
 */
@Entity
@Table(
    name = "release_suggestions",
    uniqueConstraints = [UniqueConstraint(name = "uk_release_suggestions_game_key", columnNames = ["game", "name_key"])],
)
class ReleaseSuggestion(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val game: Game,

    @Column(nullable = false, length = 150)
    var name: String,

    /** The name without punctuation, case and set codes: two sources with the same set give the same key. */
    @Column(nullable = false, length = 150)
    val nameKey: String,

    @Column(nullable = false)
    var releaseDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var source: ReleaseSource,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,

    /** The set in the card catalog, when the suggestion came from there. */
    @Column(length = 50)
    var setCode: String? = null,

    @Column(nullable = false)
    var dismissed: Boolean = false,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
