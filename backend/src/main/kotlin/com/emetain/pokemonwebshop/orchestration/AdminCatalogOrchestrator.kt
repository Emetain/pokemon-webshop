package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.dto.CatalogSyncStatusDto
import com.emetain.pokemonwebshop.dto.SyncRunDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.PokemonSpeciesRepository
import com.emetain.pokemonwebshop.repository.SyncRunRepository
import com.emetain.pokemonwebshop.service.CardSyncService
import org.springframework.stereotype.Component

@Component
class AdminCatalogOrchestrator(
    private val cardSyncService: CardSyncService,
    private val syncRunRepository: SyncRunRepository,
    private val cardSetRepository: CardSetRepository,
    private val cardRepository: CardRepository,
    private val cardPriceRepository: CardPriceRepository,
    private val speciesRepository: PokemonSpeciesRepository,
) {

    fun syncStatus(): List<CatalogSyncStatusDto> =
        Game.entries.map { game ->
            CatalogSyncStatusDto(
                game = game.slug,
                setCount = cardSetRepository.countByGame(game),
                cardCount = cardRepository.countByGame(game),
                priceCount = cardPriceRepository.countByGame(game),
                speciesCount = if (game == Game.POKEMON) speciesRepository.count() else null,
                lastRun = syncRunRepository.findFirstByGameOrderByStartedAtDescIdDesc(game)?.let(SyncRunDto::from),
            )
        }

    fun startSync(gameSlug: String): SyncRunDto {
        val game = Game.fromSlug(gameSlug) ?: throw ApiException.notFound()
        return SyncRunDto.from(cardSyncService.start(game))
    }
}
