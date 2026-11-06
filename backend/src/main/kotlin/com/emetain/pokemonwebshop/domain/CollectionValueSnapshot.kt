package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * What a collection was worth on one day, in euro cents. Card prices have no history, so this one number per
 * customer per day is what the value graph is drawn from. It can't be worked out afterwards.
 */
@Entity
@Table(
    name = "collection_value_snapshots",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_collection_value_snapshots_user_date", columnNames = ["user_id", "snapshot_date"]),
    ],
)
class CollectionValueSnapshot(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val snapshotDate: LocalDate,

    @Column(nullable = false)
    var valueCents: Long,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
