package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceSource
import com.emetain.pokemonwebshop.service.Mover
import java.time.LocalDate

/** A card in the lists of phase 12: enough for its picture, its name and a link to its page. */
data class CardRefDto(
    val id: Long,
    val game: String,
    val setCode: String,
    val setName: String,
    val number: String,
    val name: String,
    val imageUrl: String?,
    val customImage: Boolean,
) {
    companion object {
        fun from(card: Card) = CardRefDto(
            id = card.id,
            game = card.game.slug,
            setCode = card.set.code,
            setName = card.set.name,
            number = card.number,
            name = card.name,
            imageUrl = card.shownImageSmallUrl,
            customImage = card.customImageUrl != null,
        )
    }
}

/** The saved prices of one variant, one point per day, in [CardPriceHistoryDto.currency]. */
data class VariantHistoryDto(val variant: String, val source: PriceSource, val points: List<HistoryPointDto>)

/** The price graph of a card (Premium, phase 12). */
data class CardPriceHistoryDto(val currency: Currency, val variants: List<VariantHistoryDto>)

data class MoverDto(
    val card: CardRefDto,
    val variant: String,
    val quantity: Int,
    val thenCents: Long,
    val nowCents: Long,
    val changeCents: Long,
    val percent: Double?,
) {
    companion object {
        fun from(mover: Mover, card: Card) = MoverDto(
            card = CardRefDto.from(card),
            variant = mover.line.variant,
            quantity = mover.line.quantity,
            thenCents = mover.thenCents,
            nowCents = mover.nowCents,
            changeCents = mover.changeCents,
            percent = mover.percent,
        )
    }
}

/** The owned cards that rose and fell most (Premium, phase 12). [since] is the day compared with; null without history. */
data class CollectionMoversDto(
    val period: String,
    val currency: Currency,
    val since: LocalDate?,
    val risers: List<MoverDto>,
    val fallers: List<MoverDto>,
)
