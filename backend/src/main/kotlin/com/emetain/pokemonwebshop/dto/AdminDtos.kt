package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.SyncRun
import com.emetain.pokemonwebshop.domain.SyncStatus
import org.springframework.data.domain.Page
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

/** The filters of the admin list of card images, read from the URL. An empty filter means no filter. */
data class AdminImageCardRequest(
    val q: String? = null,
    val game: String? = null,
    val set: String? = null,
    /** `missing` for cards without any picture, `custom` for cards with an uploaded one. */
    val image: String? = null,
    val sort: String? = null,
    val page: Int = 0,
    /** Cards per page: 25, 50 or 100. Any other value means 25. */
    val size: Int? = null,
)

data class AdminImageCardDto(
    val id: Long,
    val game: String,
    val setCode: String,
    val setName: String,
    val number: String,
    val name: String,
    val imageUrl: String?,
    val customImage: Boolean,
    val dexNumbers: List<Int>,
) {
    companion object {
        fun from(card: Card) = AdminImageCardDto(
            id = card.id,
            game = card.game.slug,
            setCode = card.set.code,
            setName = card.set.name,
            number = card.number,
            name = card.name,
            imageUrl = card.shownImageSmallUrl,
            customImage = card.customImageUrl != null,
            dexNumbers = card.dexNumbers.sorted(),
        )
    }
}

data class AdminImageCardPageDto(
    val items: List<AdminImageCardDto>,
    val page: Int,
    val totalItems: Long,
    val totalPages: Int,
) {
    companion object {
        fun from(cards: Page<Card>) = AdminImageCardPageDto(
            items = cards.content.map(AdminImageCardDto::from),
            page = cards.number,
            totalItems = cards.totalElements,
            totalPages = cards.totalPages,
        )
    }
}
