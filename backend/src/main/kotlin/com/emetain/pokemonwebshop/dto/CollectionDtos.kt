package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardCondition
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.CollectionEntry
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.repository.OwnedLine
import com.emetain.pokemonwebshop.service.BulkAddResult
import com.emetain.pokemonwebshop.service.CollectionStats
import com.emetain.pokemonwebshop.service.CollectionValue
import com.emetain.pokemonwebshop.service.GameStats
import com.emetain.pokemonwebshop.service.Progress
import com.emetain.pokemonwebshop.service.SetStats
import com.emetain.pokemonwebshop.service.StartedSet
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class AddCollectionEntryRequest(
    val cardId: Long = 0,
    val variant: String? = null,
    val quantity: Int = 1,
    val condition: CardCondition? = null,
)

data class UpdateCollectionEntryRequest(
    val quantity: Int = 1,
    val condition: CardCondition? = null,
)

data class BulkAddRequest(
    @field:NotEmpty(message = "Kies minstens één kaart.")
    @field:Size(max = 1000, message = "Je kunt maximaal 1000 kaarten tegelijk toevoegen.")
    val cardIds: List<Long> = emptyList(),
)

data class OwnedEntryDto(
    val id: Long,
    val variant: String,
    val quantity: Int,
    val condition: CardCondition?,
) {
    companion object {
        fun listOf(entries: List<CollectionEntry>): List<OwnedEntryDto> =
            entries.sortedBy { CardVariants.displayRank(it.variant) }.map {
                OwnedEntryDto(it.id, it.variant, it.quantity, it.condition)
            }
    }
}

data class ProgressDto(val owned: Long, val total: Long, val percent: Double) {
    companion object {
        fun from(progress: Progress) = ProgressDto(progress.owned, progress.total, progress.percent)
    }
}

data class CollectionValueDto(val cents: Long, val currency: Currency, val unpricedCards: Long) {
    companion object {
        fun from(value: CollectionValue) = CollectionValueDto(value.cents, value.currency, value.unpricedCards)
    }
}

data class SetProgressDto(
    val progress: ProgressDto,
    val masterProgress: ProgressDto,
    val value: CollectionValueDto,
) {
    companion object {
        fun from(stats: SetStats) = SetProgressDto(
            progress = ProgressDto.from(stats.progress),
            masterProgress = ProgressDto.from(stats.masterProgress),
            value = CollectionValueDto.from(stats.value),
        )
    }
}

/** The answer to every change: what the customer now owns of the card, and where that leaves its set. */
data class CollectionChangeDto(
    val entries: List<OwnedEntryDto>,
    val progress: SetProgressDto,
)

data class BulkAddResultDto(
    val added: Int,
    val skipped: Int,
) {
    companion object {
        fun from(result: BulkAddResult) = BulkAddResultDto(result.added, result.skipped)
    }
}

/** Everything the card page needs to know about the viewer and this card. */
data class CardCollectionDto(
    val entries: List<OwnedEntryDto>,
    val onWishlist: Boolean,
    val inBinder: Boolean,
)

data class StartedSetDto(
    val game: String,
    val code: String,
    val name: String,
    val releaseDate: LocalDate?,
    val progress: ProgressDto,
    val masterProgress: ProgressDto,
) {
    companion object {
        fun from(started: StartedSet) = StartedSetDto(
            game = started.set.game.slug,
            code = started.set.code,
            name = started.set.name,
            releaseDate = started.set.releaseDate,
            progress = ProgressDto.from(started.progress),
            masterProgress = ProgressDto.from(started.masterProgress),
        )
    }
}

data class GameCollectionDto(
    val game: String,
    val progress: ProgressDto,
    val masterProgress: ProgressDto,
    val value: CollectionValueDto,
    val copies: Long,
) {
    companion object {
        fun from(stats: GameStats) = GameCollectionDto(
            game = stats.game.slug,
            progress = ProgressDto.from(stats.progress),
            masterProgress = ProgressDto.from(stats.masterProgress),
            value = CollectionValueDto.from(stats.value),
            copies = stats.copies,
        )
    }
}

data class CollectionOverviewDto(
    val progress: ProgressDto,
    val masterProgress: ProgressDto,
    val value: CollectionValueDto,
    val copies: Long,
    val games: List<GameCollectionDto>,
    val sets: List<StartedSetDto>,
    val mostValuable: List<CardSummaryDto>,
) {
    companion object {
        /** [cards] holds the cards of [CollectionStats.mostValuable]. They show the price of the owned variant. */
        fun from(stats: CollectionStats, cards: Map<Long, Card>) = CollectionOverviewDto(
            progress = ProgressDto.from(stats.progress),
            masterProgress = ProgressDto.from(stats.masterProgress),
            value = CollectionValueDto.from(stats.value),
            copies = stats.copies,
            games = stats.games.map(GameCollectionDto::from),
            sets = stats.sets.map(StartedSetDto::from),
            mostValuable = stats.mostValuable.mapNotNull { line -> cards[line.cardId]?.let { summary(it, line) } },
        )

        private fun summary(card: Card, line: OwnedLine) =
            CardSummaryDto.from(card, emptyList()).copy(trendCents = line.trendCents, currency = line.currency)
    }
}

/** What a collection was worth on one day, in the currency of [CollectionHistoryDto]. */
data class HistoryPointDto(val date: LocalDate, val cents: Long)

/** The value of a collection over time, oldest first. The last point is today, and is worked out live. */
data class CollectionHistoryDto(
    val currency: Currency,
    val points: List<HistoryPointDto>,
)

/** A collection someone chose to make public. Only the first name: the full name is also the name on orders. */
data class SharedCollectionDto(
    val ownerName: String,
    val collection: CollectionOverviewDto,
)
