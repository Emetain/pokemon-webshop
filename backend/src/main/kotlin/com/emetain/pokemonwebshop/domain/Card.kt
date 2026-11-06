package com.emetain.pokemonwebshop.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "cards",
    uniqueConstraints = [UniqueConstraint(name = "uk_cards_game_external_id", columnNames = ["game", "external_id"])],
    indexes = [
        Index(name = "idx_cards_set_sort_key", columnList = "set_id, sort_key"),
        Index(name = "idx_cards_game_rarity", columnList = "game, rarity"),
        Index(name = "idx_cards_game_category", columnList = "game, category"),
        Index(name = "idx_cards_game_name_search", columnList = "game, name_search"),
        Index(name = "idx_cards_name_search", columnList = "name_search"),
        Index(name = "idx_cards_sort_price_cents", columnList = "sort_price_cents"),
    ],
)
class Card(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val game: Game,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "set_id", nullable = false)
    var set: CardSet,

    @Column(nullable = false, length = 255)
    val externalId: String,

    @Column(nullable = false, length = 50)
    var number: String,

    @Column(nullable = false, length = 255)
    var name: String,

    /** The name without accents and punctuation, so "pokemon" also finds "Pokémon". */
    @Column(nullable = false, length = 255)
    var nameSearch: String,

    /** The number padded with zeros, so 2 sorts before 10. */
    @Column(nullable = false, length = 100)
    var sortKey: String,

    @Column(length = 50)
    var rarity: String? = null,

    @Column(length = 255)
    var cardType: String? = null,

    /** The kind of card in one word, such as Trainer, Creature or Leader. [cardType] has the full text. */
    @Column(length = 50)
    var category: String? = null,

    @Column(length = 500)
    var imageSmallUrl: String? = null,

    @Column(length = 500)
    var imageLargeUrl: String? = null,

    /** A picture the shop uploaded. It goes before the images of the source, and a sync never touches it. */
    @Column(length = 500)
    var customImageUrl: String? = null,

    @ElementCollection
    @CollectionTable(
        name = "card_variants",
        joinColumns = [JoinColumn(name = "card_id")],
        indexes = [Index(name = "idx_card_variants_variant", columnList = "variant")],
    )
    @Column(name = "variant", nullable = false, length = 30)
    val variants: MutableSet<String> = mutableSetOf(),

    /** Pokémon energy types, Magic colours, Lorcana inks or One Piece colours. */
    @ElementCollection
    @CollectionTable(
        name = "card_colors",
        joinColumns = [JoinColumn(name = "card_id")],
        indexes = [Index(name = "idx_card_colors_color", columnList = "color")],
    )
    @Column(name = "color", nullable = false, length = 30)
    val colors: MutableSet<String> = mutableSetOf(),

    /** National Pokédex numbers. A card can show more than one Pokémon. */
    @ElementCollection
    @CollectionTable(
        name = "card_dex_numbers",
        joinColumns = [JoinColumn(name = "card_id")],
        indexes = [Index(name = "idx_card_dex_numbers_dex_number", columnList = "dex_number")],
    )
    @Column(name = "dex_number", nullable = false)
    val dexNumbers: MutableSet<Int> = mutableSetOf(),

    /** The price shown in lists, in euro cents whatever its source, so all cards can sort together. */
    @Column
    var sortPriceCents: Long? = null,

    // A sequence instead of identity columns, because Hibernate can't batch inserts with those.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cards_seq")
    @SequenceGenerator(name = "cards_seq", sequenceName = "cards_seq", allocationSize = 500)
    val id: Long = 0,
) {
    val shownImageSmallUrl: String? get() = customImageUrl ?: imageSmallUrl

    val shownImageLargeUrl: String? get() = customImageUrl ?: imageLargeUrl
}
