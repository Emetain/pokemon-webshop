package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Game
import org.springframework.stereotype.Component

/** Downloads Cardmarket's public list of sealed products: one JSON file per game. */
@Component
class CardmarketProductProvider(
    private val http: CatalogHttpClient,
    properties: AppProperties,
) {
    private val source = properties.shop.cardmarket

    fun products(game: Game): List<CardmarketProduct> {
        val url = "${source.baseUrl}/productCatalog/productList/products_nonsingles_${CardmarketProductMapper.gameId(game)}.json"
        return CardmarketProductMapper.parse(http.getJson(url, source.requestDelay))
    }
}
