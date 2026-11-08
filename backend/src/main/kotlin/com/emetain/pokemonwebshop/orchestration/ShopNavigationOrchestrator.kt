package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.dto.ShopSeriesDetailDto
import com.emetain.pokemonwebshop.dto.ShopSeriesDto
import com.emetain.pokemonwebshop.dto.ShopSetDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.service.ProductSlugs
import org.springframework.stereotype.Component

/**
 * Series and sets for the shop navigation, from the card catalog, with how many products each has. A set that is
 * out of print is left out, unless it has something to buy.
 */
@Component
class ShopNavigationOrchestrator(
    private val cardSetRepository: CardSetRepository,
    private val productRepository: ProductRepository,
) {

    /** Newest series first: the one with the newest set. */
    fun series(gameSlug: String): List<ShopSeriesDto> {
        val game = game(gameSlug)
        val counts = productCounts(game)
        return listedSets(game, counts)
            .groupBy { it.series }
            .map { (series, sets) ->
                ShopSeriesDto(
                    slug = ProductSlugs.of(series),
                    name = series,
                    setCount = sets.size,
                    productCount = sets.sumOf { counts[it.id] ?: 0L },
                    newestRelease = sets.mapNotNull { it.releaseDate }.maxOrNull(),
                )
            }
    }

    fun seriesDetail(gameSlug: String, seriesSlug: String): ShopSeriesDetailDto {
        val game = game(gameSlug)
        val counts = productCounts(game)
        val sets = listedSets(game, counts).filter { ProductSlugs.of(it.series) == seriesSlug }
        if (sets.isEmpty()) throw ApiException.notFound("We konden deze serie niet vinden.")
        return ShopSeriesDetailDto(
            slug = seriesSlug,
            name = sets.first().series,
            sets = sets.map { ShopSetDto.from(it, counts[it.id] ?: 0L) },
        )
    }

    /** Newest first. */
    private fun listedSets(game: Game, counts: Map<Long, Long>): List<CardSet> =
        cardSetRepository.findAllNewestFirst(game).filter { it.inPrint || (counts[it.id] ?: 0L) > 0 }

    private fun productCounts(game: Game): Map<Long, Long> =
        productRepository.countListedPerSet(game).associate { it.setId to it.count }

    private fun game(slug: String): Game = Game.fromSlug(slug) ?: throw ApiException.notFound()
}
