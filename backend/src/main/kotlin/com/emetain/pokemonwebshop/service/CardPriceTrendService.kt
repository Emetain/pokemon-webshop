package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Date
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** A saved price of one variant of a card, from [com.emetain.pokemonwebshop.domain.CardPriceHistory]. */
data class SavedPrice(
    val variant: String,
    val source: PriceSource,
    val currency: Currency,
    val date: LocalDate,
    val trendCents: Long?,
)

/** The periods of "risers and fallers" (phase 12): a day, a week and a month back. */
enum class MoverPeriod(val key: String) {
    DAY("1D"),
    WEEK("1W"),
    MONTH("1M");

    fun cutoff(today: LocalDate): LocalDate = when (this) {
        DAY -> today.minusDays(1)
        WEEK -> today.minusWeeks(1)
        MONTH -> today.minusMonths(1)
    }

    companion object {
        fun of(key: String?): MoverPeriod? = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

/**
 * One owned variant with its price now and its saved price at the start of the period. [thenDate] is the last saved
 * day on or before that start, or the first saved day when the history only began later.
 */
data class MoverLine(
    val cardId: Long,
    val variant: String,
    val quantity: Int,
    val currency: Currency,
    val nowCents: Long,
    val thenCents: Long?,
    val thenDate: LocalDate?,
)

/** A card that moved: its price then and now, and the change in the viewer's currency. */
data class Mover(
    val line: MoverLine,
    val thenCents: Long,
    val nowCents: Long,
    val changeCents: Long,
    val percent: Double?,
)

data class Movers(val since: LocalDate?, val risers: List<Mover>, val fallers: List<Mover>)

object PriceTrendMath {

    /**
     * One point per day, from the first saved trend price to [today], each the last price saved on or before that day:
     * a price is only saved on the days it changes. Today's point is [liveCents] when given, the price the card page
     * shows. Converted to [to] at today's rate, like the value graph, so a moving exchange rate can't look like a moving
     * price. Empty when the conversion has no rate.
     */
    fun daily(
        saved: List<SavedPrice>,
        today: LocalDate,
        liveCents: Long?,
        to: Currency,
        rates: ExchangeRates,
    ): List<HistoryPoint> {
        val priced = saved.filter { it.trendCents != null && !it.date.isAfter(today) }.sortedBy { it.date }
        val first = priced.firstOrNull() ?: return emptyList()
        val byDate = priced.associate { it.date to it.trendCents!! }
        val points = mutableListOf<HistoryPoint>()
        var current = byDate.getValue(first.date)
        var date = first.date
        while (!date.isAfter(today)) {
            current = byDate[date] ?: current
            val cents = if (date == today && liveCents != null) liveCents else current
            points += HistoryPoint(date, rates.convertCents(cents, first.currency, to) ?: return emptyList())
            date = date.plusDays(1)
        }
        return points
    }

    /**
     * The owned cards whose price moved most in the period, up and down, by how much a copy gained or lost in euros:
     * the money a collector notices, so a common going from €0.02 to €0.10 doesn't push out a card that gained €40.
     * Each with its percentage too. Cards without a saved price before now are left out.
     */
    fun movers(lines: List<MoverLine>, cutoff: LocalDate, viewer: Currency, rates: ExchangeRates, limit: Int): Movers {
        val compared = lines.filter { it.thenCents != null && it.thenDate != null }
        val changes = compared.mapNotNull { line ->
            val then = line.thenCents!!
            val change = line.nowCents - then
            if (change == 0L) return@mapNotNull null
            val euros = rates.convertCents(change, line.currency, Currency.EUR) ?: return@mapNotNull null
            val mover = Mover(
                line = line,
                thenCents = rates.convertCents(then, line.currency, viewer) ?: return@mapNotNull null,
                nowCents = rates.convertCents(line.nowCents, line.currency, viewer) ?: return@mapNotNull null,
                changeCents = rates.convertCents(change, line.currency, viewer) ?: return@mapNotNull null,
                percent = if (then > 0) change * 100.0 / then else null,
            )
            mover to euros
        }
        val risers = changes.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Mover, Long>> { it.second }.thenBy { it.first.line.cardId })
            .map { it.first }
            .take(limit)
        val fallers = changes.filter { it.second < 0 }
            .sortedWith(compareBy<Pair<Mover, Long>> { it.second }.thenBy { it.first.line.cardId })
            .map { it.first }
            .take(limit)
        // When a card's history began after the start of the period, the comparison is "since" that day.
        val since = compared.mapNotNull { it.thenDate }.let { dates ->
            if (dates.any { it.isAfter(cutoff) }) dates.max() else cutoff
        }
        return Movers(since.takeIf { compared.isNotEmpty() }, risers, fallers)
    }
}

/**
 * Reads the saved card prices of phase 11 for the price graph per card and the risers and fallers (phase 12). Plain
 * JDBC: the history table's key starts with the card, so every lookup here is an index lookup.
 */
@Service
class CardPriceTrendService(
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
) {

    fun today(): LocalDate = clock.instant().atZone(SHOP_ZONE).toLocalDate()

    fun saved(cardId: Long): List<SavedPrice> = jdbc.query(
        "select variant, source, currency, price_date, trend_cents from card_price_history where card_id = ? " +
            "order by variant, source, price_date",
        { row, _ ->
            SavedPrice(
                variant = row.getString("variant"),
                source = PriceSource.valueOf(row.getString("source")),
                currency = Currency.valueOf(row.getString("currency")),
                date = row.getDate("price_date").toLocalDate(),
                trendCents = row.getLong("trend_cents").takeUnless { row.wasNull() },
            )
        },
        cardId,
    )

    /**
     * Every owned variant with a trend price, and its saved price on [cutoff]: the last one on or before that day, or
     * the first one saved when its history began later. A variant with prices from two sources counts once.
     */
    fun moverLines(userId: Long, cutoff: LocalDate): List<MoverLine> = jdbc.query(
        MOVER_LINES,
        { row, _ ->
            val before = row.getDate("before_date")?.toLocalDate()
            MoverLine(
                cardId = row.getLong("card_id"),
                variant = row.getString("variant"),
                quantity = row.getInt("quantity"),
                currency = Currency.valueOf(row.getString("currency")),
                nowCents = row.getLong("trend_cents"),
                thenCents = if (before != null) row.getLong("before_cents") else row.getLong("first_cents").takeUnless { row.wasNull() },
                thenDate = before ?: row.getDate("first_date")?.toLocalDate(),
            )
        },
        Date.valueOf(cutoff),
        Date.valueOf(cutoff),
        userId,
    ).distinctBy { it.cardId to it.variant }

    private companion object {
        val SHOP_ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")

        private const val SAME_PRICE = "h.card_id = p.card_id and h.variant = p.variant and h.source = p.source " +
            "and h.trend_cents is not null"

        val MOVER_LINES = """
            select e.card_id, e.variant, e.quantity, p.currency, p.trend_cents,
              (select h.trend_cents from card_price_history h where $SAME_PRICE and h.price_date <= ?
                 order by h.price_date desc fetch first 1 rows only) as before_cents,
              (select max(h.price_date) from card_price_history h where $SAME_PRICE and h.price_date <= ?) as before_date,
              (select h.trend_cents from card_price_history h where $SAME_PRICE
                 order by h.price_date fetch first 1 rows only) as first_cents,
              (select min(h.price_date) from card_price_history h where $SAME_PRICE) as first_date
            from collection_entries e
            join card_prices p on p.card_id = e.card_id and p.variant = e.variant
            where e.user_id = ? and p.trend_cents is not null
            order by e.card_id, e.variant, p.source
        """.trimIndent()
    }
}
