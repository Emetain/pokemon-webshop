package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/**
 * One single from Cardmarket's public daily files, with its price guide (phase 11). The files have one product
 * per print but no card number, so this is what cards are linked to, see [CardmarketLink]. Replaced as a whole
 * at every sync of its game.
 */
@Entity
@Table(
    name = "cardmarket_singles",
    indexes = [Index(name = "idx_cardmarket_singles_game_expansion", columnList = "game, expansion_id")],
)
class CardmarketSingle(
    /** Cardmarket's `idProduct`. Unique across all games. */
    @Id
    val productId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val game: Game,

    /** Card name with its attacks, such as "Mega Rayquaza ex [Roar of the Ruler | Storm Emeralda]". */
    @Column(nullable = false, length = 255)
    val name: String,

    @Column(nullable = false)
    val expansionId: Long,

    /** The same card across its prints and sets. Prints of one card in one expansion share it. */
    @Column
    val metacardId: Long? = null,

    @Column
    val trendCents: Long? = null,

    @Column
    val averageCents: Long? = null,

    /** Cardmarket's second column: the foil printing of this product, which is the reverse holo for Pokémon. */
    @Column
    val foilTrendCents: Long? = null,

    @Column
    val foilAverageCents: Long? = null,

    /** When Cardmarket made the price guide. */
    @Column(nullable = false)
    val updatedAt: Instant,
)
