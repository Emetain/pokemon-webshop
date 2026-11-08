package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.repository.CardSetRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate

/**
 * What the shop knows about a set beyond the card catalog: whether it is still printed, and how popular it is.
 * Both come from `app.shop`. The card sync applies them to every set it saves, and startup applies them to all
 * sets, so a changed setting counts after a restart.
 */
@Component
class ShopSetRules(
    properties: AppProperties,
    private val cardSetRepository: CardSetRepository,
    private val transactions: TransactionTemplate,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val inPrintFrom = properties.shop.inPrintFrom
    private val popularSets = properties.shop.popularSets

    /** A set without a release date stays in the shop: there is nothing to judge it on. */
    fun inPrint(game: Game, releaseDate: LocalDate?): Boolean {
        val from = inPrintFrom[game] ?: return true
        return releaseDate == null || !releaseDate.isBefore(from)
    }

    fun popularRank(game: Game, code: String): Int? = popularSets[game]?.indexOf(code)?.takeIf { it >= 0 }

    /** Returns whether anything changed. */
    fun apply(set: CardSet): Boolean {
        val inPrint = inPrint(set.game, set.releaseDate)
        val rank = popularRank(set.game, set.code)
        if (set.inPrint == inPrint && set.popularRank == rank) return false
        set.inPrint = inPrint
        set.popularRank = rank
        return true
    }

    @EventListener(ApplicationReadyEvent::class)
    fun applyToAllSets() {
        val changed = transactions.execute { cardSetRepository.findAll().count(::apply) }
        if (changed != null && changed > 0) log.info("Print status or popularity changed for {} sets", changed)
    }
}
