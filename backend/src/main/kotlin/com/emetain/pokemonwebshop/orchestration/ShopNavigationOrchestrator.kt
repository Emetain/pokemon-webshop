package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.dto.ShopSeriesDetailDto
import com.emetain.pokemonwebshop.dto.ShopSeriesDto
import com.emetain.pokemonwebshop.dto.ShopSetDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.service.ProductSlugs
import org.springframework.stereotype.Component

/** Series and sets for the shop navigation, from the card catalog, with how many products each has. */
@Component
class ShopNavigationOrchestrator(
    private val cardSetRepository: CardSetRepository,
    private val productRepository: ProductRepository,
) {

    /** Newest series first: the one with the newest set. */
    fun series(gameSlug: String): List<ShopSeriesDto> {
        val game = game(gameSlug)
        val counts = productCounts(game)
        return cardSetRepository.findAllNewestFirst(game)
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
        val sets = cardSetRepository.findAllNewestFirst(game).filter { ProductSlugs.of(it.series) == seriesSlug }
        if (sets.isEmpty()) throw ApiException.notFound("We konden deze serie niet vinden.")
        val counts = productCounts(game)
        return ShopSeriesDetailDto(
            slug = seriesSlug,
            name = sets.first().series,
            sets = sets.map { ShopSetDto.from(it, counts[it.id] ?: 0L) },
        )
    }

    private fun productCounts(game: Game): Map<Long, Long> =
        productRepository.countPerSet(game).associate { it.setId to it.count }

    private fun game(slug: String): Game = Game.fromSlug(slug) ?: throw ApiException.notFound()
}
