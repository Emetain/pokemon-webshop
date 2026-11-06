package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
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
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/** The copies of one variant of a card that a customer owns. */
@Entity
@Table(
    name = "collection_entries",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_collection_entries_user_card_variant",
            columnNames = ["user_id", "card_id", "variant"],
        ),
    ],
    indexes = [Index(name = "idx_collection_entries_card_id", columnList = "card_id")],
)
class CollectionEntry(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    val card: Card,

    @Column(nullable = false, length = 30)
    val variant: String,

    @Column(nullable = false)
    var quantity: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "card_condition", length = 20)
    var condition: CardCondition? = null,

    @Column(nullable = false, updatable = false)
    val addedAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
