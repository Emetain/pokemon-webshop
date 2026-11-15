package com.emetain.pokemonwebshop.service.pictures

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.service.provider.CatalogHttpClient
import tools.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import java.time.LocalDate

/** A set at TCGplayer ("group"). */
data class TcgGroup(val id: Long, val name: String, val abbreviation: String?, val publishedOn: LocalDate?)

/** A card or sealed product at TCGplayer. Cards have a [number], sealed products don't. */
data class TcgProduct(val id: Long, val name: String, val number: String?)

/** TCGplayer's catalog: sets and their products. Behind an interface, so tests never call tcgcsv.com. */
interface TcgplayerCatalog {
    fun groups(game: Game): List<TcgGroup>

    fun products(game: Game, groupId: Long): List<TcgProduct>
}

/** TCGplayer's picture of a product, from its CDN: the same address the Riftbound sync uses. */
object TcgplayerImages {
    private const val BASE = "https://tcgplayer-cdn.tcgplayer.com/product/"

    fun small(productId: Long) = "$BASE${productId}_200w.jpg"

    fun large(productId: Long) = "$BASE${productId}_in_1000x1000.jpg"
}

/**
 * TCGplayer's catalog through tcgcsv.com, the free daily copy the Riftbound sync uses already. English products
 * only: Japanese Pokémon is a separate catalog with other set names.
 */
@Component
class TcgcsvCatalog(
    private val http: CatalogHttpClient,
    properties: AppProperties,
) : TcgplayerCatalog {

    private val source = properties.catalog.tcgcsv

    override fun groups(game: Game): List<TcgGroup> =
        http.getJson("${source.baseUrl}/tcgplayer/${category(game)}/groups", source.requestDelay).path("results").values().map { group ->
            TcgGroup(
                id = group.path("groupId").asLong(),
                name = group.path("name").asString(),
                abbreviation = group.path("abbreviation").asString("").ifBlank { null },
                publishedOn = group.path("publishedOn").asString("").take(DATE_LENGTH)
                    .let { runCatching { LocalDate.parse(it) }.getOrNull() },
            )
        }

    override fun products(game: Game, groupId: Long): List<TcgProduct> =
        http.getJson("${source.baseUrl}/tcgplayer/${category(game)}/$groupId/products", source.requestDelay).path("results").values().map { product ->
            TcgProduct(
                id = product.path("productId").asLong(),
                name = product.path("name").asString(),
                number = number(product),
            )
        }

    private fun number(product: JsonNode): String? =
        product.path("extendedData").firstOrNull { it.path("name").asString() == "Number" }?.path("value")?.asString()?.ifBlank { null }

    private companion object {
        const val DATE_LENGTH = 10

        /** TCGplayer's category ids. */
        fun category(game: Game): Int = when (game) {
            Game.MTG -> 1
            Game.POKEMON -> 3
            Game.ONE_PIECE -> 68
            Game.LORCANA -> 71
            Game.RIFTBOUND -> 89
        }
    }
}
