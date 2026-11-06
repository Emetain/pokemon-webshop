package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.SyncRun
import com.emetain.pokemonwebshop.domain.SyncStatus
import java.time.Instant

data class AdminStatusDto(
    val displayName: String,
)

data class SyncRunDto(
    val id: Long,
    val game: String,
    val status: SyncStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val setsProcessed: Int,
    val cardsCreated: Int,
    val cardsUpdated: Int,
    val cardsUnchanged: Int,
    val cardsFailed: Int,
    val pricesStored: Int,
    val error: String?,
) {
    companion object {
        fun from(run: SyncRun) = SyncRunDto(
            id = run.id,
            game = run.game.slug,
            status = run.status,
            startedAt = run.startedAt,
            finishedAt = run.finishedAt,
            setsProcessed = run.setsProcessed,
            cardsCreated = run.cardsCreated,
            cardsUpdated = run.cardsUpdated,
            cardsUnchanged = run.cardsUnchanged,
            cardsFailed = run.cardsFailed,
            pricesStored = run.pricesStored,
            error = run.error,
        )
    }
}

data class CatalogSyncStatusDto(
    val game: String,
    val setCount: Long,
    val cardCount: Long,
    val priceCount: Long,
    /** Pokémon only: how many slots the Pokédex has. */
    val speciesCount: Long?,
    val lastRun: SyncRunDto?,
)

/** The picture a card or set now shows, and whether that is one the shop uploaded. */
data class CatalogImageDto(
    val imageUrl: String?,
    val custom: Boolean,
)
