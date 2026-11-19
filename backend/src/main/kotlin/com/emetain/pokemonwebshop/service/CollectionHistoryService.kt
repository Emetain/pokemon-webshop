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

    /**
     * At most [days] back, for a customer without Premium (phase 11). The last point before the cutoff stays as the
     * line's start, so a full year still has a value on its first day.
     */
    fun limit(points: List<HistoryPoint>, today: LocalDate, days: Long): LimitedHistory {
        val cutoff = today.minusDays(days)
        val before = points.filter { !it.date.isAfter(cutoff) }
        if (before.size <= 1 && before.firstOrNull()?.date?.isBefore(cutoff) != true) return LimitedHistory(points, false)
        val start = before.last()
        val within = points.filter { it.date.isAfter(cutoff) }
        return LimitedHistory(listOf(HistoryPoint(cutoff, start.cents)) + within, true)
    }
}

/** [hasOlder]: there are points from before the cutoff, which Premium would show. */
data class LimitedHistory(val points: List<HistoryPoint>, val hasOlder: Boolean)

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

    /** The graph's points; without Premium only the last [limitDays] days, see [CollectionHistoryMath.limit]. */
    @Transactional(readOnly = true)
    fun history(userId: Long, currency: Currency, limitDays: Long?): LimitedHistory {
        val points = history(userId, currency)
        return if (limitDays == null) LimitedHistory(points, false) else CollectionHistoryMath.limit(points, today(), limitDays)
    }

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
