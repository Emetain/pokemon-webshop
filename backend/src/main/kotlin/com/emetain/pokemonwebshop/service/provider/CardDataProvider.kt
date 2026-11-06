package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.PriceSource
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

/** Reads the catalog of one game from an outside source. Swap an implementation to change the source. */
interface CardDataProvider {
    val game: Game

    /** Calls [onSet] once per set, so a run can save its progress and never holds everything twice. */
    fun fetchCatalog(onSet: (ProviderSet) -> Unit)
}

data class ProviderSet(
    val externalId: String,
    val code: String,
    val name: String,
    val series: String,
    val releaseDate: LocalDate?,
    val cardCount: Int,
    val logoUrl: String?,
    val symbolUrl: String?,
    val sortOrder: Int,
    val cards: List<ProviderCard>,
    /** Cards the source failed to deliver. They keep their existing data until the next run. */
    val failedCards: Int = 0,
)

data class ProviderCard(
    val externalId: String,
    val number: String,
    val name: String,
    val rarity: String?,
    val cardType: String?,
    val category: String? = null,
    val imageSmallUrl: String?,
    val imageLargeUrl: String?,
    val variants: Set<String>,
    val colors: Set<String> = emptySet(),
    val dexNumbers: Set<Int> = emptySet(),
    val prices: List<ProviderPrice> = emptyList(),
)

data class ProviderPrice(
    val variant: String,
    val trendCents: Long?,
    val averageCents: Long?,
    val updatedAt: Instant,
    val source: PriceSource = PriceSource.CARDMARKET,
    val currency: Currency = Currency.EUR,
)

@Component
class CardDataProviders(providers: List<CardDataProvider>) {
    private val providersByGame = providers.associateBy { it.game }

    fun forGame(game: Game): CardDataProvider = providersByGame.getValue(game)
}
