package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/** A market price in cents of [currency]. Exists only when the source really has a price for this variant. */
@Entity
@Table(
    name = "card_prices",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_card_prices_card_variant_source", columnNames = ["card_id", "variant", "source"]),
    ],
)
class CardPrice(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    val card: Card,

    @Column(nullable = false, length = 30)
    val variant: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val source: PriceSource,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    val currency: Currency,

    @Column
    var trendCents: Long? = null,

    @Column
    var averageCents: Long? = null,

    @Column(nullable = false)
    var updatedAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "card_prices_seq")
    @SequenceGenerator(name = "card_prices_seq", sequenceName = "card_prices_seq", allocationSize = 500)
    val id: Long = 0,
)
