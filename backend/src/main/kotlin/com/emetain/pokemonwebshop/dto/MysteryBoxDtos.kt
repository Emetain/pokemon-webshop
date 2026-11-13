package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.MysteryBoxBatch
import com.emetain.pokemonwebshop.domain.MysteryBoxDraw
import com.emetain.pokemonwebshop.domain.MysteryBoxPrize
import com.emetain.pokemonwebshop.service.MysteryBoxMath
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * What the product page of a mystery box shows: the batch boxes come from now, with the chance of every kind. Only
 * chances, never how many boxes there are or are left: the shop doesn't show its stock.
 */
data class MysteryBoxDto(
    val batchName: String,
    /** Of the boxes that are left: the lowest, the highest and the average value. */
    val minValueCents: Long,
    val maxValueCents: Long,
    val averageValueCents: Long,
    val prizes: List<MysteryPrizeDto>,
) {
    companion object {
        fun from(batch: MysteryBoxBatch): MysteryBoxDto {
            val left = batch.prizes.filter { it.remaining > 0 }
            val chances = MysteryBoxMath.chances(batch.prizes.map { it.remaining })
            return MysteryBoxDto(
                batchName = batch.name,
                minValueCents = left.minOfOrNull { it.valueCents } ?: 0,
                maxValueCents = left.maxOfOrNull { it.valueCents } ?: 0,
                averageValueCents = if (batch.remaining == 0) 0 else left.sumOf { it.valueCents * it.remaining } / batch.remaining,
                prizes = batch.prizes.mapIndexed { index, prize -> MysteryPrizeDto.from(prize, chances[index]) },
            )
        }
    }
}

data class MysteryPrizeDto(
    val name: String,
    val valueCents: Long,
    /** In percent, of the boxes left in the batch. */
    val chance: Double,
    /** None of this kind is left. */
    val gone: Boolean,
    val cardImageUrl: String?,
) {
    companion object {
        fun from(prize: MysteryBoxPrize, chance: Double) = MysteryPrizeDto(
            name = prize.name,
            valueCents = prize.valueCents,
            chance = chance,
            gone = prize.remaining <= 0,
            cardImageUrl = prize.card?.let { it.shownImageSmallUrl ?: it.shownImageLargeUrl },
        )
    }
}

/** A batch as the admin sees it: every kind of box, and which order got which box. */
data class AdminMysteryBatchDto(
    val id: Long,
    val name: String,
    val createdAt: Instant,
    val boxCount: Int,
    val remaining: Int,
    val prizes: List<AdminMysteryPrizeDto>,
    val draws: List<MysteryDrawDto>,
) {
    companion object {
        fun from(batch: MysteryBoxBatch, draws: List<MysteryBoxDraw>): AdminMysteryBatchDto {
            val chances = MysteryBoxMath.chances(batch.prizes.map { it.remaining })
            return AdminMysteryBatchDto(
                id = batch.id,
                name = batch.name,
                createdAt = batch.createdAt,
                boxCount = batch.boxCount,
                remaining = batch.remaining,
                prizes = batch.prizes.mapIndexed { index, prize ->
                    AdminMysteryPrizeDto(prize.id, prize.name, prize.valueCents, prize.count, prize.drawn, prize.remaining, chances[index], prize.card?.id)
                },
                draws = draws.map { MysteryDrawDto(it.orderLine.order.number, it.prize.name, it.drawnAt) },
            )
        }
    }
}

data class AdminMysteryPrizeDto(
    val id: Long,
    val name: String,
    val valueCents: Long,
    val count: Int,
    val drawn: Int,
    val remaining: Int,
    val chance: Double,
    val cardId: Long?,
)

data class MysteryDrawDto(val orderNumber: String, val prizeName: String, val drawnAt: Instant)

data class MysteryBoxBatchRequest(
    @field:Size(max = 100, message = "De naam mag maximaal 100 tekens hebben.")
    val name: String = "",
    @field:Valid
    val prizes: List<MysteryPrizeRequest> = emptyList(),
)

data class MysteryPrizeRequest(
    @field:Size(max = 150, message = "Een omschrijving mag maximaal 150 tekens hebben.")
    val name: String = "",
    val valueCents: Long = 0,
    val count: Int = 1,
    val cardId: Long? = null,
)
