package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "card_sets",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_card_sets_game_external_id", columnNames = ["game", "external_id"]),
        UniqueConstraint(name = "uk_card_sets_game_code", columnNames = ["game", "code"]),
    ],
    indexes = [Index(name = "idx_card_sets_game_release_date", columnList = "game, release_date")],
)
class CardSet(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val game: Game,

    @Column(nullable = false, length = 100)
    val externalId: String,

    @Column(nullable = false, length = 50)
    var code: String,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(nullable = false, length = 100)
    var series: String,

    @Column
    var releaseDate: LocalDate? = null,

    @Column(nullable = false)
    var cardCount: Int = 0,

    @Column(length = 500)
    var logoUrl: String? = null,

    @Column(length = 500)
    var symbolUrl: String? = null,

    /** A picture the shop uploaded. It goes before the logo of the source, and a sync never touches it. */
    @Column(length = 500)
    var customLogoUrl: String? = null,

    /** Orders sets without a release date. A higher number is newer. */
    @Column(nullable = false)
    var sortOrder: Int = 0,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    val shownLogoUrl: String? get() = customLogoUrl ?: logoUrl
}
