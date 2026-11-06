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
import java.time.Instant

/** One run of the card sync for one game. The counts grow while the run is busy. */
@Entity
@Table(
    name = "sync_runs",
    indexes = [Index(name = "idx_sync_runs_game_started_at", columnList = "game, started_at")],
)
class SyncRun(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val game: Game,

    @Column(nullable = false, updatable = false)
    val startedAt: Instant,

    @Column
    var finishedAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SyncStatus = SyncStatus.RUNNING,

    @Column(nullable = false)
    var setsProcessed: Int = 0,

    @Column(nullable = false)
    var cardsCreated: Int = 0,

    @Column(nullable = false)
    var cardsUpdated: Int = 0,

    @Column(nullable = false)
    var cardsUnchanged: Int = 0,

    @Column(nullable = false)
    var cardsFailed: Int = 0,

    @Column(nullable = false)
    var pricesStored: Int = 0,

    @Column(length = 2000)
    var error: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
