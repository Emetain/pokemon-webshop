package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CardSummaryDto
import com.emetain.pokemonwebshop.dto.ScanReadingDto
import com.emetain.pokemonwebshop.dto.ScanResultDto
import com.emetain.pokemonwebshop.dto.ScannerStatusDto
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.service.ScanOutcome
import com.emetain.pokemonwebshop.repository.CollectionEntryRepository
import com.emetain.pokemonwebshop.service.CardOfferService
import com.emetain.pokemonwebshop.service.PremiumService
import com.emetain.pokemonwebshop.service.ScanService
import com.emetain.pokemonwebshop.domain.Game
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class ScannerOrchestrator(
    private val scanService: ScanService,
    private val premiumService: PremiumService,
    private val cardPriceRepository: CardPriceRepository,
    private val collectionEntryRepository: CollectionEntryRepository,
    private val cardOfferService: CardOfferService,
    private val cardRepository: CardRepository,
    private val transaction: TransactionTemplate,
    properties: AppProperties,
) {
    private val freeScans = properties.premium.freeScans

    fun status(user: User): ScannerStatusDto {
        val premium = premiumService.has(user)
        return ScannerStatusDto(scanService.available(), premium, if (premium) null else scanService.freeLeft(user.id), freeScans)
    }

    /**
     * The scan runs outside a transaction (it waits for the model); the answer is made in one of its own, with the
     * cards loaded again, because their variants are read lazily.
     */
    fun scan(user: User, photo: ByteArray, gameHint: Game?): ScanResultDto {
        val outcome = scanService.scan(user, photo, gameHint)
        return transaction.execute { summaries(user, outcome) }!!
    }

    private fun summaries(user: User, outcome: ScanOutcome): ScanResultDto {
        val ids = outcome.candidates.map { it.card.id }
        val loaded = cardRepository.findAllWithSetByIdIn(ids).associateBy { it.id }
        val cards = ids.mapNotNull { loaded[it] }
        val prices = cardPriceRepository.findAllByCardIds(ids).groupBy { it.card.id }
        val owned = collectionEntryRepository.findAllByUserAndCards(user.id, ids).groupBy { it.card.id }
        val offers = cardOfferService.cheapestPerCard(ids)
        val summaries = cards.associate { card ->
            card.id to CardSummaryDto.from(card, prices[card.id].orEmpty(), owned[card.id].orEmpty(), offers[card.id])
        }
        val reading = outcome.reading
        return ScanResultDto(
            reading = ScanReadingDto(reading.game?.slug, reading.name, reading.setCode, reading.setName, reading.number, reading.finish),
            match = outcome.match?.let { summaries[it.card.id] },
            candidates = cards.mapNotNull { summaries[it.id] },
            scansLeft = outcome.scansLeft,
        )
    }
}
