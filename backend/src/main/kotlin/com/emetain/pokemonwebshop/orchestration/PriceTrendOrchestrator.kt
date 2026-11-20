package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CardPriceHistoryDto
import com.emetain.pokemonwebshop.dto.CollectionMoversDto
import com.emetain.pokemonwebshop.dto.HistoryPointDto
import com.emetain.pokemonwebshop.dto.MoverDto
import com.emetain.pokemonwebshop.dto.VariantHistoryDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.service.CardPriceTrendService
import com.emetain.pokemonwebshop.service.ExchangeRateService
import com.emetain.pokemonwebshop.service.HistoryPoint
import com.emetain.pokemonwebshop.service.MoverPeriod
import com.emetain.pokemonwebshop.service.PremiumService
import com.emetain.pokemonwebshop.service.PriceTrendMath
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** The price graph of a card and the risers and fallers of a collection (both Premium, phase 12). */
@Component
class PriceTrendOrchestrator(
    private val trendService: CardPriceTrendService,
    private val premiumService: PremiumService,
    private val cardRepository: CardRepository,
    private val cardPriceRepository: CardPriceRepository,
    private val exchangeRateService: ExchangeRateService,
    properties: AppProperties,
) {
    private val moversShown = properties.premium.moversShown

    /**
     * One line per variant, from the source the card page shows now: a card whose price moved from Cardmarket to
     * TCGplayer (phase 11) would otherwise jump between two markets.
     */
    @Transactional(readOnly = true)
    fun cardHistory(user: User, cardId: Long): CardPriceHistoryDto {
        premiumService.require(user, "De prijsgeschiedenis van een kaart hoort bij Premium.")
        val card = cardRepository.findById(cardId).orElseThrow { ApiException.notFound("Deze kaart bestaat niet.") }
        val saved = trendService.saved(card.id)
        val current = cardPriceRepository.findAllByCardIds(listOf(card.id))
        val rates = exchangeRateService.current()
        val today = trendService.today()
        val variants = CardVariants.displaySorted((saved.map { it.variant } + current.map { it.variant }).toSet())
        return CardPriceHistoryDto(
            currency = user.currency,
            variants = variants.mapNotNull { variant ->
                val price = current.filter { it.variant == variant && it.trendCents != null }.minByOrNull { it.source }
                val source = price?.source ?: saved.filter { it.variant == variant }.maxByOrNull { it.date }?.source
                    ?: return@mapNotNull null
                val rows = saved.filter { it.variant == variant && it.source == source }
                val points = PriceTrendMath.daily(rows, today, price?.trendCents, user.currency, rates).ifEmpty {
                    // Priced, but not saved yet: one point, which the page shows as "no history yet".
                    listOfNotNull(
                        price?.let { rates.convertCents(it.trendCents!!, it.currency, user.currency) }?.let { HistoryPoint(today, it) },
                    )
                }
                VariantHistoryDto(variant, source, points.map { HistoryPointDto(it.date, it.cents) }).takeIf { points.isNotEmpty() }
            },
        )
    }

    @Transactional(readOnly = true)
    fun movers(user: User, periodKey: String?): CollectionMoversDto {
        premiumService.require(user, "Stijgers en dalers horen bij Premium.")
        val period = MoverPeriod.of(periodKey) ?: throw ApiException.validation("Kies 1D, 1W of 1M.")
        val cutoff = period.cutoff(trendService.today())
        val movers = PriceTrendMath.movers(
            trendService.moverLines(user.id, cutoff),
            cutoff,
            user.currency,
            exchangeRateService.current(),
            moversShown,
        )
        val ids = (movers.risers + movers.fallers).map { it.line.cardId }.toSet()
        val cards = if (ids.isEmpty()) emptyMap() else cardRepository.findAllWithSetByIdIn(ids).associateBy { it.id }
        return CollectionMoversDto(
            period = period.key,
            currency = user.currency,
            since = movers.since,
            risers = movers.risers.mapNotNull { mover -> cards[mover.line.cardId]?.let { MoverDto.from(mover, it) } },
            fallers = movers.fallers.mapNotNull { mover -> cards[mover.line.cardId]?.let { MoverDto.from(mover, it) } },
        )
    }
}
