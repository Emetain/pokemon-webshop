package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDate

data class CardPriceHistoryId(
    val cardId: Long = 0,
    val variant: String = "",
    val source: PriceSource = PriceSource.CARDMARKET,
    val priceDate: LocalDate = LocalDate.MIN,
) : Serializable

/**
 * A card's price on the day it changed (phase 11), for the price graphs, risers and alerts of phase 12. Only written
 * when a price differs from the one saved before it, so a price that stays the same costs no row; the price of any day
 * is the last row on or before it. History can't be filled in afterwards. Written with plain JDBC by
 * [com.emetain.pokemonwebshop.service.CardPriceHistoryService]: the key starts with the card, which is also what
 * "the prices of one card over time" looks up, so the table needs no other index.
 */
@Entity
@Table(name = "card_price_history")
@IdClass(CardPriceHistoryId::class)
class CardPriceHistory(
    @Id
    @Column(nullable = false)
    val cardId: Long,

    @Id
    @Column(nullable = false, length = 30)
    val variant: String,

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val source: PriceSource,

    @Id
    @Column(nullable = false)
    val priceDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    val currency: Currency,

    @Column
    val trendCents: Long? = null,

    @Column
    val averageCents: Long? = null,
)
