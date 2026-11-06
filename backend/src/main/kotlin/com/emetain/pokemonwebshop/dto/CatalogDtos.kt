package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardPrice
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.CollectionEntry
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.ExchangeRate
import com.emetain.pokemonwebshop.domain.PriceSource
import org.springframework.data.domain.Page
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class CardSetDto(
    val game: String,
    val code: String,
    val name: String,
    val series: String,
    val releaseDate: LocalDate?,
    val cardCount: Int,
    val logoUrl: String?,
    val symbolUrl: String?,
) {
    companion object {
        fun from(set: CardSet) = CardSetDto(
            game = set.game.slug,
            code = set.code,
            name = set.name,
            series = set.series,
            releaseDate = set.releaseDate,
            cardCount = set.cardCount,
            logoUrl = set.logoUrl,
            symbolUrl = set.symbolUrl,
        )
    }
}

/** The filters and paging of a card list, read from the URL. An empty filter means no filter. */
data class CardListRequest(
    val rarity: String? = null,
    val variant: String? = null,
    val category: String? = null,
    val color: String? = null,
    val priced: Boolean = false,
    /** `owned` or `missing`, for a logged-in customer. Anyone else gets all cards. */
    val collection: String? = null,
    /** Whether `missing` means a variant is missing, as in master set progress. */
    val master: Boolean = false,
    val sort: String? = null,
    val page: Int = 0,
    val size: Int = 60,
)

/** A set with the filter options that exist in it. */
data class CardSetDetailDto(
    val set: CardSetDto,
    val rarities: List<String>,
    val variants: List<String>,
    val categories: List<String>,
    val colors: List<String>,
)

data class CardSummaryDto(
    val id: Long,
    val game: String,
    val setCode: String,
    val setName: String,
    val number: String,
    val name: String,
    val rarity: String?,
    val imageUrl: String?,
    val trendCents: Long?,
    val currency: Currency?,
    val variants: List<String>,
    /** What the logged-in customer owns of this card. Null for a visitor who isn't logged in. */
    val owned: List<OwnedEntryDto>?,
) {
    companion object {
        fun from(card: Card, prices: List<CardPrice>, owned: List<CollectionEntry>? = null): CardSummaryDto {
            val price = CardVariants.displayPrice(prices)
            return CardSummaryDto(
                id = card.id,
                game = card.game.slug,
                setCode = card.set.code,
                setName = card.set.name,
                number = card.number,
                name = card.name,
                rarity = card.rarity,
                imageUrl = card.imageSmallUrl,
                trendCents = price?.trendCents,
                currency = price?.currency,
                variants = CardVariants.displaySorted(card.variants),
                owned = owned?.let(OwnedEntryDto::listOf),
            )
        }
    }
}

data class CardPriceDto(
    val variant: String,
    val source: PriceSource,
    val currency: Currency,
    val trendCents: Long?,
    val averageCents: Long?,
    val updatedAt: Instant,
) {
    companion object {
        fun from(price: CardPrice) = CardPriceDto(
            variant = price.variant,
            source = price.source,
            currency = price.currency,
            trendCents = price.trendCents,
            averageCents = price.averageCents,
            updatedAt = price.updatedAt,
        )
    }
}

data class CardDetailDto(
    val id: Long,
    val game: String,
    val set: CardSetDto,
    val number: String,
    val name: String,
    val rarity: String?,
    val cardType: String?,
    val imageSmallUrl: String?,
    val imageLargeUrl: String?,
    val variants: List<String>,
    val colors: List<String>,
    val dexNumbers: List<Int>,
    val prices: List<CardPriceDto>,
) {
    companion object {
        fun from(card: Card, prices: List<CardPrice>) = CardDetailDto(
            id = card.id,
            game = card.game.slug,
            set = CardSetDto.from(card.set),
            number = card.number,
            name = card.name,
            rarity = card.rarity,
            cardType = card.cardType,
            imageSmallUrl = card.imageSmallUrl,
            imageLargeUrl = card.imageLargeUrl,
            variants = CardVariants.displaySorted(card.variants),
            colors = card.colors.sorted(),
            dexNumbers = card.dexNumbers.sorted(),
            prices = prices.sortedBy { CardVariants.displayRank(it.variant) }.map(CardPriceDto::from),
        )
    }
}

data class CardPageDto(
    val items: List<CardSummaryDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
) {
    companion object {
        /** [ownedByCard] is null for a visitor who isn't logged in. */
        fun from(
            cards: Page<Card>,
            pricesByCard: Map<Long, List<CardPrice>>,
            ownedByCard: Map<Long, List<CollectionEntry>>? = null,
        ) = CardPageDto(
            items = cards.content.map { card ->
                val owned = ownedByCard?.let { it[card.id].orEmpty() }
                CardSummaryDto.from(card, pricesByCard[card.id].orEmpty(), owned)
            },
            page = cards.number,
            size = cards.size,
            totalItems = cards.totalElements,
            totalPages = cards.totalPages,
        )
    }
}

data class ExchangeRateDto(
    val currency: Currency,
    val perEuro: BigDecimal,
    val date: LocalDate,
) {
    companion object {
        fun from(rate: ExchangeRate) = ExchangeRateDto(rate.currency, rate.perEuro, rate.rateDate)
    }
}

/** The lists are the filter options of the chosen game, empty when no game is chosen. */
data class CardSearchResultDto(
    val results: CardPageDto,
    val rarities: List<String>,
    val categories: List<String>,
    val colors: List<String>,
)
