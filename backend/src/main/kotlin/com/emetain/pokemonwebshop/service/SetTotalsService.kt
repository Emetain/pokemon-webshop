package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.repository.CardRepository
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * What a complete set holds: [numbers] for normal progress and [variants] for master set progress. These come
 * from the cards we really have and not from `CardSet.cardCount`, because some sources announce more cards than
 * they deliver and those sets could never reach 100%.
 */
data class SetTotals(val setId: Long, val game: Game, val numbers: Long, val variants: Long)

/** Counting the whole catalog takes over a second, and it only changes during a sync. So it stays in memory. */
@Service
class SetTotalsService(
    private val cardRepository: CardRepository,
    properties: AppProperties,
    clock: Clock,
) {

    private val cache = TimedCache(properties.collection.cacheTtl, clock, ::load)

    fun all(): Map<Long, SetTotals> = cache.get()

    private fun load(): Map<Long, SetTotals> {
        val variants = cardRepository.countVariantsPerSet().associate { it.setId to it.count }
        return cardRepository.countNumbersPerSet().associate {
            it.setId to SetTotals(it.setId, it.game, it.count, variants[it.setId] ?: 0)
        }
    }
}
