package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
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

/**
 * A binder a Premium member makes next to their collection, for example a trade binder, with its own value (phase 12).
 * The collection stays what counts for progress, the Pokédex, the value graph and the home binder. When Premium ends,
 * the binders stay visible but can't be changed, and nothing is deleted.
 */
@Entity
@Table(name = "extra_binders", indexes = [Index(name = "idx_extra_binders_user", columnList = "user_id")])
class ExtraBinder(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, length = 60)
    var name: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)

/** Copies of one variant of a card in an [ExtraBinder]. */
@Entity
@Table(
    name = "extra_binder_entries",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_extra_binder_entries_binder_card_variant", columnNames = ["binder_id", "card_id", "variant"]),
    ],
    indexes = [Index(name = "idx_extra_binder_entries_card", columnList = "card_id")],
)
class ExtraBinderEntry(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "binder_id", nullable = false)
    val binder: ExtraBinder,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    val card: Card,

    @Column(nullable = false, length = 30)
    val variant: String,

    @Column(nullable = false)
    var quantity: Int,

    @Column(nullable = false, updatable = false)
    val addedAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
