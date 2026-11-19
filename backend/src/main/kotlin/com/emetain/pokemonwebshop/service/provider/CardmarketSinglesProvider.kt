package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.CardmarketSingle
import com.emetain.pokemonwebshop.domain.Game
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Clock

/**
 * Cardmarket's public daily files for singles: the product list and the price guide, on the host the sealed product
 * import already uses (phase 11). Both are about 15 MB, so they are read one entry at a time.
 */
@Component
class CardmarketSinglesProvider(
    private val http: CatalogHttpClient,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    properties: AppProperties,
) {
    private val source = properties.shop.cardmarket

    fun singles(game: Game): List<CardmarketSingle> {
        val id = CardmarketProductMapper.gameId(game)
        val products = http.get("${source.baseUrl}/productCatalog/productList/products_singles_$id.json", source.requestDelay) {
            CardmarketSinglesParser.products(objectMapper.createParser(it))
        }
        val guide = http.get("${source.baseUrl}/productCatalog/priceGuide/price_guide_$id.json", source.requestDelay) {
            CardmarketSinglesParser.priceGuide(objectMapper.createParser(it))
        }
        return CardmarketSinglesParser.combine(game, products, guide, clock.instant())
    }
}
