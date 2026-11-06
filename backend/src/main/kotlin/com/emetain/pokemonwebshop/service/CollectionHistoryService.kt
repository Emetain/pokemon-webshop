package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.CollectionValueSnapshot
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.repository.CollectionEntryRepository
import com.emetain.pokemonwebshop.repository.CollectionValueSnapshotRepository
import com.emetain.pokemonwebshop.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

data class HistoryPoint(val date: LocalDate, val cents: Long)

object CollectionHistoryMath {

    /**
     * The points of a graph, oldest first. Saved values are in euros and become [currency] at today's rate, so a
     * moving exchange rate can't look like a moving collection. Today's point is the live value, which replaces
     * a snapshot of today: that way a card added this afternoon moves the line right away.
     */
    fun points(
        saved: List<HistoryPoint>,
        today: LocalDate,
        live: CollectionValue,
        rates: ExchangeRates,
    ): List<HistoryPoint> {
        val past = saved.filter { it.date.isBefore(today) }
            .sortedBy { it.date }
            .mapNotNull { point ->
                rates.convertCents(point.cents, Currency.EUR, live.currency)?.let { HistoryPoint(point.date, it) }
            }
        return past + HistoryPoint(today, live.cents)
    }
}

/** Remembers what collections are worth, because card prices themselves have no history. */
@Service
class CollectionHistoryService(
    private val snapshotRepository: CollectionValueSnapshotRepository,
    private val entryRepository: CollectionEntryRepository,
    private val userRepository: UserRepository,
    private val statsService: CollectionStatsService,
    private val exchangeRateService: ExchangeRateService,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun history(userId: Long, currency: Currency): List<HistoryPoint> = CollectionHistoryMath.points(
        saved = snapshotRepository.findAllByUserIdOrderBySnapshotDate(userId).map { HistoryPoint(it.snapshotDate, it.valueCents) },
        today = today(),
        live = statsService.value(userId, currency),
        rates = exchangeRateService.current(),
    )

    fun usersWithACollection(): List<Long> = entryRepository.findUserIdsWithEntries()

    /** One snapshot per day: running this again today replaces it. */
    @Transactional
    fun snapshot(userId: Long) {
        val today = today()
        val cents = statsService.value(userId, Currency.EUR).cents
        val existing = snapshotRepository.findByUserIdAndSnapshotDate(userId, today)
        if (existing != null) {
            existing.valueCents = cents
        } else {
            snapshotRepository.save(CollectionValueSnapshot(userRepository.getReferenceById(userId), today, cents))
        }
    }

    private fun today(): LocalDate = clock.instant().atZone(SHOP_ZONE).toLocalDate()

    private companion object {
        val SHOP_ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
    }
}
