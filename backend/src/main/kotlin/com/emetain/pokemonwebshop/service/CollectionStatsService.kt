package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.CollectionEntryRepository
import com.emetain.pokemonwebshop.repository.OwnedLine
import com.emetain.pokemonwebshop.repository.OwnedPrice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class SetStats(val progress: Progress, val masterProgress: Progress, val value: CollectionValue)

data class StartedSet(val set: CardSet, val progress: Progress, val masterProgress: Progress)

data class GameStats(
    val game: Game,
    val progress: Progress,
    val masterProgress: Progress,
    val value: CollectionValue,
    val copies: Long,
)

data class CollectionStats(
    val progress: Progress,
    val masterProgress: Progress,
    val value: CollectionValue,
    val copies: Long,
    val games: List<GameStats>,
    val sets: List<StartedSet>,
    val mostValuable: List<OwnedLine>,
)

/** Works out how far a collection is and what it's worth. */
@Service
class CollectionStatsService(
    private val entryRepository: CollectionEntryRepository,
    private val cardPriceRepository: CardPriceRepository,
    private val cardRepository: CardRepository,
    private val cardSetRepository: CardSetRepository,
    private val setTotalsService: SetTotalsService,
    private val exchangeRateService: ExchangeRateService,
) {

    @Transactional(readOnly = true)
    fun overview(userId: Long, currency: Currency): CollectionStats {
        val totals = setTotalsService.all().values
        val numbers = entryRepository.countOwnedNumbersPerSet(userId).associate { it.setId to it.count }
        val variants = entryRepository.countOwnedVariantsPerSet(userId).associate { it.setId to it.count }
        val lines = withPrices(entryRepository.findOwnedLines(userId), cardPriceRepository.findOwnedPrices(userId))
        val rates = exchangeRateService.current()

        val games = Game.entries.map { game ->
            val sets = totals.filter { it.game == game }
            val gameLines = lines.filter { it.game == game }
            GameStats(
                game = game,
                progress = Progress(sets.sumOf { numbers[it.setId] ?: 0 }, sets.sumOf { it.numbers }),
                masterProgress = Progress(sets.sumOf { variants[it.setId] ?: 0 }, sets.sumOf { it.variants }),
                value = CollectionValueCalculator.value(gameLines, currency, rates),
                copies = gameLines.sumOf { it.quantity.toLong() },
            )
        }

        return CollectionStats(
            progress = games.map { it.progress }.reduce(Progress::plus),
            masterProgress = games.map { it.masterProgress }.reduce(Progress::plus),
            value = CollectionValueCalculator.value(lines, currency, rates),
            copies = lines.sumOf { it.quantity.toLong() },
            games = games,
            sets = startedSets(numbers, variants),
            mostValuable = CollectionValueCalculator.mostValuable(lines, rates, MOST_VALUABLE_CARDS),
        )
    }

    /** What a whole collection is worth right now. */
    @Transactional(readOnly = true)
    fun value(userId: Long, currency: Currency): CollectionValue = CollectionValueCalculator.value(
        withPrices(entryRepository.findOwnedLines(userId), cardPriceRepository.findOwnedPrices(userId)),
        currency,
        exchangeRateService.current(),
    )

    /** The sets of a game the customer has cards of. */
    @Transactional(readOnly = true)
    fun startedSets(userId: Long, game: Game): List<StartedSet> {
        val numbers = entryRepository.countOwnedNumbersPerSet(userId).associate { it.setId to it.count }
        val variants = entryRepository.countOwnedVariantsPerSet(userId).associate { it.setId to it.count }
        return startedSets(numbers, variants).filter { it.set.game == game }
    }

    /** Counts one set directly, so a change shows right away. */
    @Transactional(readOnly = true)
    fun set(userId: Long, setId: Long, currency: Currency): SetStats {
        val lines = withPrices(
            entryRepository.findOwnedLinesInSet(userId, setId),
            cardPriceRepository.findOwnedPricesInSet(userId, setId),
        )
        return SetStats(
            progress = Progress(entryRepository.countOwnedNumbersInSet(userId, setId), cardRepository.countNumbersInSet(setId)),
            masterProgress = Progress(
                entryRepository.countOwnedVariantsInSet(userId, setId),
                cardRepository.countVariantsInSet(setId),
            ),
            value = CollectionValueCalculator.value(lines, currency, exchangeRateService.current()),
        )
    }

    private fun startedSets(numbers: Map<Long, Long>, variants: Map<Long, Long>): List<StartedSet> {
        val totals = setTotalsService.all()
        return cardSetRepository.findAllById(numbers.keys)
            .map { set ->
                val total = totals[set.id]
                StartedSet(
                    set = set,
                    progress = Progress(numbers[set.id] ?: 0, total?.numbers ?: 0),
                    masterProgress = Progress(variants[set.id] ?: 0, total?.variants ?: 0),
                )
            }
            .sortedWith(
                compareBy<StartedSet> { it.set.game }
                    .thenByDescending { it.progress.percent }
                    .thenBy { it.set.name },
            )
    }

    /** A variant that has no price of its own stays without a price, even if another variant has one. */
    private fun withPrices(lines: List<OwnedLine>, prices: List<OwnedPrice>): List<OwnedLine> {
        val byVariant = prices.associateBy { it.cardId to it.variant }
        return lines.map { line ->
            val price = byVariant[line.cardId to line.variant]
            line.copy(trendCents = price?.trendCents, currency = price?.currency)
        }
    }

    private companion object {
        const val MOST_VALUABLE_CARDS = 12
    }
}
