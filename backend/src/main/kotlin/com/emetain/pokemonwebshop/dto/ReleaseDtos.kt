package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.SetRelease
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

/** A release on the calendar, with its pre-orders. */
data class SetReleaseDto(
    val id: Long,
    val game: String,
    val name: String,
    val releaseDate: LocalDate,
    /** Expected, not announced: show the month with "verwacht". */
    val tentative: Boolean,
    val description: String?,
    val imageUrl: String?,
    val set: CardSetDto?,
    val preOrders: List<ProductSummaryDto>,
)

/** The release calendar: what is coming, and what came out in the last two weeks. */
data class ReleaseCalendarDto(
    val upcoming: List<SetReleaseDto>,
    val recent: List<SetReleaseDto>,
)

data class AdminSetReleaseDto(
    val id: Long,
    val game: String,
    val name: String,
    val releaseDate: LocalDate,
    val tentative: Boolean,
    val description: String?,
    val imageUrl: String?,
    val setCode: String?,
    val setName: String?,
    val published: Boolean,
    val products: List<AdminReleaseProductDto>,
    val updatedAt: Instant,
) {
    companion object {
        fun from(release: SetRelease) = AdminSetReleaseDto(
            id = release.id,
            game = release.game.slug,
            name = release.name,
            releaseDate = release.releaseDate,
            tentative = release.tentative,
            description = release.description,
            imageUrl = release.imageUrl,
            setCode = release.cardSet?.code,
            setName = release.cardSet?.name,
            published = release.published,
            products = release.products.map { AdminReleaseProductDto(it.id, it.name, it.preOrder, it.status.name) },
            updatedAt = release.updatedAt,
        )
    }
}

data class AdminReleaseProductDto(val id: Long, val name: String, val preOrder: Boolean, val status: String)

data class SetReleaseRequest(
    val game: String = "",
    @field:Size(max = 150, message = "De naam mag maximaal 150 tekens hebben.")
    val name: String = "",
    val releaseDate: LocalDate? = null,
    val tentative: Boolean = false,
    @field:Size(max = 1000, message = "De omschrijving mag maximaal 1000 tekens hebben.")
    val description: String? = null,
    /** The code of the set in the card catalog, when it is there already. */
    @field:Size(max = 50, message = "Deze setcode is te lang.")
    val setCode: String? = null,
    val published: Boolean = true,
    val productIds: List<Long> = emptyList(),
)
