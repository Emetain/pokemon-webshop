package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardPrice
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.ExchangeRateRepository
import com.emetain.pokemonwebshop.service.provider.ProviderCard
import com.emetain.pokemonwebshop.service.provider.ProviderPrice
import com.emetain.pokemonwebshop.service.provider.ProviderSet
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ImportResult(
    val created: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
    val pricesStored: Int = 0,
    val failed: Int = 0,
) {
    operator fun plus(other: ImportResult) = ImportResult(
        created + other.created,
        updated + other.updated,
        unchanged + other.unchanged,
        pricesStored + other.pricesStored,
        failed + other.failed,
    )
}

/**
 * Saves one set at a time, so a run that stops halfway keeps what it finished. Running it twice changes
 * nothing. Cards are never deleted, because collections and products in later phases point at them.
 */
@Service
class CatalogImportService(
    private val cardSetRepository: CardSetRepository,
    private val cardRepository: CardRepository,
    private val cardPriceRepository: CardPriceRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val shopSetRules: ShopSetRules,
) {

    @Transactional
    fun importSet(game: Game, source: ProviderSet): ImportResult {
        val set = upsertSet(game, source)
        val rates = ExchangeRates(exchangeRateRepository.findAll().associate { it.currency to it.perEuro })
        return source.cards.distinctBy { it.externalId }
            .chunked(LOOKUP_CHUNK_SIZE)
            .map { importCards(game, set, it, rates) }
            .fold(ImportResult(failed = source.failedCards), ImportResult::plus)
    }

    private fun upsertSet(game: Game, source: ProviderSet): CardSet {
        val externalId = source.externalId.cut(100)
        val set = cardSetRepository.findByGameAndExternalId(game, externalId)
            ?: CardSet(game = game, externalId = externalId, code = source.code, name = source.name, series = source.series)
        set.code = source.code.cut(50)
        set.name = source.name.cut(255)
        set.series = source.series.cut(100)
        set.releaseDate = source.releaseDate
        set.cardCount = source.cardCount
        set.logoUrl = source.logoUrl?.cut(500)
        set.symbolUrl = source.symbolUrl?.cut(500)
        set.sortOrder = source.sortOrder
        shopSetRules.apply(set)
        return cardSetRepository.save(set)
    }

    private fun importCards(
        game: Game,
        set: CardSet,
        sources: List<ProviderCard>,
        rates: ExchangeRates,
    ): ImportResult {
        val existing = cardRepository.findAllByGameAndExternalIdIn(game, sources.map { it.externalId.cut(255) })
            .associateBy { it.externalId }
        val pricesByCard = cardPriceRepository.findAllByCardIn(existing.values).groupBy { it.card.id }

        var created = 0
        var updated = 0
        var unchanged = 0
        var pricesStored = 0

        for (source in sources) {
            val card = existing[source.externalId.cut(255)]
            val saved = if (card == null) {
                created++
                cardRepository.save(newCard(game, set, source))
            } else {
                if (applyChanges(card, set, source)) updated++ else unchanged++
                card
            }
            pricesStored += syncPrices(saved, pricesByCard[saved.id].orEmpty(), source.prices)
            saved.sortPriceCents = sortPrice(source.prices, rates)
        }
        return ImportResult(created, updated, unchanged, pricesStored)
    }

    private fun newCard(game: Game, set: CardSet, source: ProviderCard) = Card(
        game = game,
        set = set,
        externalId = source.externalId.cut(255),
        number = source.number.cut(50),
        name = source.name.cut(255),
        nameSearch = CatalogText.nameSearch(source.name).cut(255),
        sortKey = CatalogText.sortKey(source.number).cut(100),
        rarity = source.rarity?.cut(50),
        cardType = source.cardType?.cut(255),
        category = source.category?.cut(50),
        imageSmallUrl = source.imageSmallUrl?.cut(500),
        imageLargeUrl = source.imageLargeUrl?.cut(500),
        variants = source.variants.toMutableSet(),
        colors = source.colors.map { it.cut(30) }.toMutableSet(),
        dexNumbers = source.dexNumbers.toMutableSet(),
    )

    /** Only touches what differs, so an unchanged card costs no database write. Returns whether anything did. */
    private fun applyChanges(card: Card, set: CardSet, source: ProviderCard): Boolean {
        val wanted = newCard(card.game, set, source)
        val changed = card.set.id != set.id ||
            card.number != wanted.number ||
            card.name != wanted.name ||
            card.nameSearch != wanted.nameSearch ||
            card.sortKey != wanted.sortKey ||
            card.rarity != wanted.rarity ||
            card.cardType != wanted.cardType ||
            card.category != wanted.category ||
            card.imageSmallUrl != wanted.imageSmallUrl ||
            card.imageLargeUrl != wanted.imageLargeUrl ||
            card.variants != wanted.variants ||
            card.colors != wanted.colors ||
            card.dexNumbers != wanted.dexNumbers
        if (!changed) return false

        card.set = set
        card.number = wanted.number
        card.name = wanted.name
        card.nameSearch = wanted.nameSearch
        card.sortKey = wanted.sortKey
        card.rarity = wanted.rarity
        card.cardType = wanted.cardType
        card.category = wanted.category
        card.imageSmallUrl = wanted.imageSmallUrl
        card.imageLargeUrl = wanted.imageLargeUrl
        card.variants.replaceWith(wanted.variants)
        card.colors.replaceWith(wanted.colors)
        card.dexNumbers.replaceWith(wanted.dexNumbers)
        return true
    }

    /** A price the source no longer has is removed: showing an old price would be making one up. */
    private fun syncPrices(card: Card, existing: List<CardPrice>, sources: List<ProviderPrice>): Int {
        val wanted = sources.distinctBy { it.variant to it.source }
        val existingByKey = existing.associateBy { it.variant to it.source }

        for (source in wanted) {
            val price = existingByKey[source.variant to source.source]
            if (price == null) {
                cardPriceRepository.save(
                    CardPrice(
                        card = card,
                        variant = source.variant,
                        source = source.source,
                        currency = source.currency,
                        trendCents = source.trendCents,
                        averageCents = source.averageCents,
                        updatedAt = source.updatedAt,
                    ),
                )
            } else {
                price.trendCents = source.trendCents
                price.averageCents = source.averageCents
                price.updatedAt = source.updatedAt
            }
        }

        val wantedKeys = wanted.map { it.variant to it.source }.toSet()
        cardPriceRepository.deleteAll(existing.filter { (it.variant to it.source) !in wantedKeys })
        return wanted.size
    }

    /** The price lists show, in euro cents. Without a rate a dollar price sorts as is, which is close enough. */
    private fun sortPrice(prices: List<ProviderPrice>, rates: ExchangeRates): Long? {
        val price = prices.filter { it.trendCents != null }.minByOrNull { CardVariants.displayRank(it.variant) }
        val cents = price?.trendCents ?: return null
        return rates.convertCents(cents, price.currency, Currency.EUR) ?: cents
    }

    private fun String.cut(length: Int): String = take(length)

    private fun <T> MutableSet<T>.replaceWith(values: Set<T>) {
        if (this != values) {
            clear()
            addAll(values)
        }
    }

    private companion object {
        const val LOOKUP_CHUNK_SIZE = 1000
    }
}
