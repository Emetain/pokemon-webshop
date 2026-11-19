package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.CardPrice
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Date
import java.sql.Types
import java.time.LocalDate

/**
 * Saves card prices per day while a sync imports them (phase 11), only when they differ from what was saved last.
 * [CardPrice.recordedOn] and the two recorded amounts remember that, so it costs no lookups. A second sync on the same
 * day overwrites that day's row instead of adding one. Plain JDBC batches: tens of thousands of rows a night.
 */
@Service
class CardPriceHistoryService(private val jdbc: JdbcTemplate) {

    /** Marks [price] as recorded today when it changed, and returns what to write; null when nothing changed. */
    fun pending(price: CardPrice, today: LocalDate): PendingHistory? {
        val unchanged = price.recordedOn != null &&
            price.recordedTrendCents == price.trendCents &&
            price.recordedAverageCents == price.averageCents
        if (unchanged || (price.trendCents == null && price.averageCents == null)) return null
        price.recordedOn = today
        price.recordedTrendCents = price.trendCents
        price.recordedAverageCents = price.averageCents
        return PendingHistory(price, today)
    }

    /**
     * Call after the prices and their cards are flushed: the rows point at the cards. Updates today's row where one
     * exists (a second sync that day, or a price an admin linked again), and inserts the rest.
     */
    fun write(rows: List<PendingHistory>) {
        if (rows.isEmpty()) return
        val updated = jdbc.batchUpdate(UPDATE, rows, rows.size) { statement, row ->
            statement.setString(1, row.price.currency.name)
            statement.setObject(2, row.price.trendCents, Types.BIGINT)
            statement.setObject(3, row.price.averageCents, Types.BIGINT)
            statement.setLong(4, row.price.card.id)
            statement.setString(5, row.price.variant)
            statement.setString(6, row.price.source.name)
            statement.setDate(7, Date.valueOf(row.date))
        }.flatMap { it.toList() }
        val inserts = rows.filterIndexed { index, _ -> updated.getOrElse(index) { 0 } == 0 }
        if (inserts.isEmpty()) return
        jdbc.batchUpdate(INSERT, inserts, inserts.size) { statement, row ->
            statement.setLong(1, row.price.card.id)
            statement.setString(2, row.price.variant)
            statement.setString(3, row.price.source.name)
            statement.setDate(4, Date.valueOf(row.date))
            statement.setString(5, row.price.currency.name)
            statement.setObject(6, row.price.trendCents, Types.BIGINT)
            statement.setObject(7, row.price.averageCents, Types.BIGINT)
        }
    }

    private companion object {
        const val INSERT = "insert into card_price_history (card_id, variant, source, price_date, currency, trend_cents, " +
            "average_cents) values (?, ?, ?, ?, ?, ?, ?)"
        const val UPDATE = "update card_price_history set currency = ?, trend_cents = ?, average_cents = ? " +
            "where card_id = ? and variant = ? and source = ? and price_date = ?"
    }
}

data class PendingHistory(val price: CardPrice, val date: LocalDate)
