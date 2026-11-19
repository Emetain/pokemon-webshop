package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardPrice
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.CardmarketSingle

/** A Cardmarket product an admin can link a card to. */
data class CardmarketCandidateDto(
    val productId: Long,
    val name: String,
    val trendCents: Long?,
    val foilTrendCents: Long?,
) {
    companion object {
        fun from(single: CardmarketSingle) =
            CardmarketCandidateDto(single.productId, single.name, single.trendCents, single.foilTrendCents)
    }
}

/** A card whose Cardmarket product is doubtful or was set by hand, with the products it could be. */
data class CardmarketLinkDto(
    val cardId: Long,
    /** The game's slug, for the link to the card page. */
    val game: String,
    val setCode: String,
    val setName: String,
    val number: String,
    val name: String,
    val rarity: String?,
    val imageUrl: String?,
    val link: String?,
    val product: CardmarketCandidateDto?,
    /** What customers see now, with its source. */
    val prices: List<CardPriceDto>,
    val candidates: List<CardmarketCandidateDto>,
) {
    companion object {
        fun from(card: Card, prices: List<CardPrice>, product: CardmarketSingle?, candidates: List<CardmarketSingle>) =
            CardmarketLinkDto(
                cardId = card.id,
                game = card.game.slug,
                setCode = card.set.code,
                setName = card.set.name,
                number = card.number,
                name = card.name,
                rarity = card.rarity,
                imageUrl = card.shownImageSmallUrl,
                link = card.cardmarketLink?.name,
                product = product?.let(CardmarketCandidateDto::from),
                prices = prices.sortedBy { CardVariants.displayRank(it.variant) }.map(CardPriceDto::from),
                candidates = candidates.map(CardmarketCandidateDto::from),
            )
    }
}

data class CardmarketLinkPageDto(
    val items: List<CardmarketLinkDto>,
    val page: Int,
    val totalPages: Int,
    val totalItems: Long,
    val doubtful: Long,
    val manual: Long,
)

/** [productId] null: no Cardmarket product fits, the card shows its TCGplayer price. */
data class SetCardmarketLinkRequest(val productId: Long?)
