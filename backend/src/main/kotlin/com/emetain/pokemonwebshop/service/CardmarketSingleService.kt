package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.CardmarketSingle
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.service.provider.CardmarketSinglesProvider
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.sql.Types

/**
 * Keeps the Cardmarket singles of a game in our database (phase 11), replaced as a whole once per sync. Plain JDBC
 * batches, because about 76,000 Pokémon singles one entity at a time would take minutes.
 */
@Service
class CardmarketSingleService(
    private val provider: CardmarketSinglesProvider,
    private val jdbc: JdbcTemplate,
    private val transaction: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Downloads today's files. A failure keeps yesterday's rows, which still link cards and price them with the date
     * they had, so it never stops the sync. Returns how many singles there are now, or null when it failed.
     */
    fun refresh(game: Game): Int? =
        try {
            val singles = provider.singles(game)
            if (singles.isEmpty()) {
                log.warn("Cardmarket's singles list for {} came back empty; keeping the current one", game)
                null
            } else {
                replace(game, singles)
                log.info("Stored {} Cardmarket singles for {}", singles.size, game)
                singles.size
            }
        } catch (exception: RuntimeException) {
            log.warn("Keeping the current Cardmarket singles for {}: {}", game, exception.message)
            null
        }

    fun replace(game: Game, singles: List<CardmarketSingle>) {
        transaction.executeWithoutResult {
            jdbc.update("delete from cardmarket_singles where game = ?", game.name)
            singles.chunked(BATCH_SIZE).forEach { chunk ->
                jdbc.batchUpdate(INSERT, chunk, chunk.size) { statement, single ->
                    statement.setLong(1, single.productId)
                    statement.setString(2, single.game.name)
                    statement.setString(3, single.name)
                    statement.setLong(4, single.expansionId)
                    statement.setObject(5, single.metacardId, Types.BIGINT)
                    statement.setObject(6, single.trendCents, Types.BIGINT)
                    statement.setObject(7, single.averageCents, Types.BIGINT)
                    statement.setObject(8, single.foilTrendCents, Types.BIGINT)
                    statement.setObject(9, single.foilAverageCents, Types.BIGINT)
                    statement.setTimestamp(10, Timestamp.from(single.updatedAt))
                }
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 1000
        const val INSERT = "insert into cardmarket_singles (product_id, game, name, expansion_id, metacard_id, " +
            "trend_cents, average_cents, foil_trend_cents, foil_average_cents, updated_at) " +
            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    }
}
