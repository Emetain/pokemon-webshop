package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.PictureSuggestion
import com.emetain.pokemonwebshop.domain.ReleaseSuggestion
import com.emetain.pokemonwebshop.service.pictures.PictureSearchStatus
import com.emetain.pokemonwebshop.service.pictures.TcgplayerImages
import java.time.Instant
import java.time.LocalDate

data class PictureSearchStatusDto(
    val running: Boolean,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val setsTotal: Int,
    val setsDone: Int,
    val setsNotFound: Int,
    val cardsFilled: Int,
    val productsFilled: Int,
    val suggested: Int,
    val notFound: Int,
    val skipped: Int,
    val error: String?,
) {
    companion object {
        fun from(status: PictureSearchStatus) = PictureSearchStatusDto(
            running = status.running,
            startedAt = status.startedAt,
            finishedAt = status.finishedAt,
            setsTotal = status.setsTotal,
            setsDone = status.setsDone,
            setsNotFound = status.setsNotFound,
            cardsFilled = status.cardsFilled,
            productsFilled = status.productsFilled,
            suggested = status.suggested,
            notFound = status.notFound,
            skipped = status.skipped,
            error = status.error,
        )
    }
}

/** Per game: what has no picture, what waits for the admin, and the last search. */
data class PictureSearchOverviewDto(
    val game: String,
    val cardsWithoutPicture: Long,
    val productsWithoutPicture: Long,
    val pendingSuggestions: Long,
    val search: PictureSearchStatusDto?,
)

data class PictureCandidateDto(
    val tcgplayerId: Long,
    val name: String,
    val score: Int,
    val imageUrl: String,
    val largeImageUrl: String,
)

data class PictureSuggestionDto(
    val id: Long,
    val game: String,
    /** `CARD` or `PRODUCT`. */
    val kind: String,
    val name: String,
    /** The card number, or the product type. */
    val detail: String?,
    val setName: String?,
    /** For a product: its page in admin. */
    val productId: Long?,
    val candidates: List<PictureCandidateDto>,
) {
    companion object {
        fun from(suggestion: PictureSuggestion): PictureSuggestionDto {
            val card = suggestion.card
            val product = suggestion.product
            return PictureSuggestionDto(
                id = suggestion.id,
                game = suggestion.game.slug,
                kind = if (card != null) "CARD" else "PRODUCT",
                name = card?.name ?: product?.name.orEmpty(),
                detail = card?.number ?: product?.type?.name,
                setName = card?.set?.name ?: product?.cardSet?.name,
                productId = product?.id,
                candidates = suggestion.candidates.map {
                    PictureCandidateDto(it.tcgplayerId, it.name, it.score, TcgplayerImages.small(it.tcgplayerId), TcgplayerImages.large(it.tcgplayerId))
                },
            )
        }
    }
}

data class PictureSuggestionPageDto(
    val suggestions: List<PictureSuggestionDto>,
    val page: Int,
    val totalPages: Int,
    val total: Long,
)

data class AcceptPictureRequest(
    val tcgplayerId: Long = 0,
)

data class ReleaseSuggestionDto(
    val id: Long,
    val game: String,
    val name: String,
    val releaseDate: LocalDate,
    /** `CATALOG` or `TCGPLAYER`. */
    val source: String,
    val setCode: String?,
) {
    companion object {
        fun from(suggestion: ReleaseSuggestion) = ReleaseSuggestionDto(
            id = suggestion.id,
            game = suggestion.game.slug,
            name = suggestion.name,
            releaseDate = suggestion.releaseDate,
            source = suggestion.source.name,
            setCode = suggestion.setCode,
        )
    }
}
