package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardPrice
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.CardmarketLink
import com.emetain.pokemonwebshop.domain.CardmarketSingle
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.PriceSource
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardmarketSingleRepository
import com.emetain.pokemonwebshop.repository.ExchangeRateRepository
import com.emetain.pokemonwebshop.service.provider.ProviderCard
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A card on the admin page of price links, with what it needs to show. */
data class LinkReview(
    val card: Card,
    val prices: List<CardPrice>,
    val product: CardmarketSingle?,
    val candidates: List<CardmarketSingle>,
)

/**
 * The shop's own links from Pokémon and Magic cards to Cardmarket products (phase 11): fed to [CardmarketLinks] during
 * a sync, and the admin page where doubtful links are fixed by hand.
 */
@Service
class CardmarketLinkService(
    private val cardRepository: CardRepository,
    private val singleRepository: CardmarketSingleRepository,
    private val cardPriceRepository: CardPriceRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
) {

    /** Runs inside the import's transaction. Returns nothing for games whose prices aren't linked by the shop. */
    fun decide(game: Game, cards: List<ProviderCard>, rates: ExchangeRates): Map<String, LinkDecision> {
        if (game !in CardmarketLinks.GAMES || cards.isEmpty()) return emptyMap()
        val manual = cards.map { it.externalId }.chunked(LOOKUP_CHUNK_SIZE)
            .flatMap { cardRepository.findManualLinks(game, it) }
            .associate { it.externalId to ManualLink(it.productId) }
        val wanted = cards.mapNotNull { it.cardmarketProductId } + manual.values.mapNotNull { it.productId }
        return CardmarketLinks.decide(game, cards, manual, singlesAround(game, wanted.toSet()), rates)
    }

    /** The products asked for, and every other single of their expansions: the candidates for shared products. */
    fun singlesAround(game: Game, productIds: Set<Long>): Map<Long, CardmarketSingle> {
        if (productIds.isEmpty()) return emptyMap()
        val direct = productIds.chunked(LOOKUP_CHUNK_SIZE).flatMap { singleRepository.findAllById(it) }
        val expansions = direct.map { it.expansionId }.toSet()
        val around = if (expansions.isEmpty()) emptyList() else singleRepository.findAllByGameAndExpansionIdIn(game, expansions)
        return (direct + around).associateBy { it.productId }
    }

    @Transactional(readOnly = true)
    fun reviews(game: Game, link: CardmarketLink, page: Int): Page<LinkReview> {
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            PAGE_SIZE,
            Sort.by(Sort.Order.desc("set.releaseDate"), Sort.Order.asc("set.id"), Sort.Order.asc("sortKey")),
        )
        val cards = cardRepository.findAllByGameAndCardmarketLink(game, link, pageable)
        val prices = cardPriceRepository.findAllByCardIds(cards.content.map { it.id }).groupBy { it.card.id }
        val singlesBySet = cards.content.map { it.set.id }.distinct().associateWith { setId ->
            singlesAround(game, cardRepository.findCardmarketProductIdsInSet(setId).toSet())
        }
        return cards.map { card ->
            val singles = singlesBySet[card.set.id].orEmpty()
            val product = card.cardmarketProductId?.let { singles[it] ?: singleRepository.findById(it).orElse(null) }
            LinkReview(card, prices[card.id].orEmpty(), product, candidates(card, singles))
        }
    }

    fun count(game: Game, link: CardmarketLink): Long = cardRepository.countByGameAndCardmarketLink(game, link)

    /**
     * Links a card by hand, and prices it from that product right away. [productId] null means no product fits: the
     * Cardmarket price goes, and the TCGplayer price comes with the next sync if the card had none yet.
     */
    @Transactional
    fun setManual(cardId: Long, productId: Long?): LinkReview {
        val card = linkedCard(cardId)
        val single = productId?.let {
            singleRepository.findById(it).orElse(null)?.takeIf { single -> single.game == card.game }
                ?: throw ApiException.validation("Dit Cardmarket-product kennen we niet.")
        }
        card.cardmarketLink = CardmarketLink.MANUAL
        card.cardmarketProductId = productId

        val existing = cardPriceRepository.findAllByCardIds(listOf(card.id))
        val firstEdition = existing.filter { it.source == PriceSource.CARDMARKET && it.variant == CardVariants.FIRST_EDITION }
        val keep = if (single == null) {
            existing.filter { it.source == PriceSource.TCGPLAYER } + firstEdition
        } else {
            val prices = CardmarketLinks.pricesOf(card.variants, single)
            val stale = existing.filter { it !in firstEdition }
            cardPriceRepository.deleteAll(stale)
            cardPriceRepository.flush()
            firstEdition + cardPriceRepository.saveAll(
                prices.map {
                    CardPrice(
                        card = card,
                        variant = it.variant,
                        source = it.source,
                        currency = it.currency,
                        trendCents = it.trendCents,
                        averageCents = it.averageCents,
                        updatedAt = it.updatedAt,
                    )
                },
            )
        }
        if (single == null) cardPriceRepository.deleteAll(existing.filter { it !in keep })
        card.sortPriceCents = sortPrice(keep)
        return review(card, keep, single)
    }

    /** Gives the card back to the sync, which decides its link again on its next run. */
    @Transactional
    fun reset(cardId: Long) {
        val card = linkedCard(cardId)
        card.cardmarketLink = null
        card.cardmarketProductId = null
    }

    private fun review(card: Card, prices: List<CardPrice>, product: CardmarketSingle?): LinkReview {
        val singles = singlesAround(card.game, cardRepository.findCardmarketProductIdsInSet(card.set.id).toSet())
        return LinkReview(card, prices, product, candidates(card, singles))
    }

    /** The products with the card's name in the expansions of its set, cheapest prints first in Cardmarket's order. */
    private fun candidates(card: Card, singles: Map<Long, CardmarketSingle>): List<CardmarketSingle> {
        val name = CatalogText.nameSearch(card.name)
        return singles.values.filter { CardmarketLinks.baseName(it) == name }.sortedBy { it.productId }
    }

    private fun linkedCard(cardId: Long): Card =
        cardRepository.findWithSetById(cardId)?.takeIf { it.game in CardmarketLinks.GAMES } ?: throw ApiException.notFound()

    private fun sortPrice(prices: List<CardPrice>): Long? {
        val price = CardVariants.displayPrice(prices) ?: return null
        val rates = ExchangeRates(exchangeRateRepository.findAll().associate { it.currency to it.perEuro })
        return rates.convertCents(price.trendCents!!, price.currency, Currency.EUR) ?: price.trendCents
    }

    private companion object {
        const val LOOKUP_CHUNK_SIZE = 1000
        const val PAGE_SIZE = 25
    }
}
