package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.service.CatalogText
import com.fasterxml.jackson.databind.JsonNode

/** One sealed product from Cardmarket's public product list. It has no language, release date or image. */
data class CardmarketProduct(
    val id: Long,
    val name: String,
    val category: String,
    val expansionId: Long,
)

/** A set of our catalog, by the name products start with. */
data class CatalogSetName(val id: Long, val name: String)

data class PlannedProduct(val product: CardmarketProduct, val type: ProductType, val setId: Long)

/** What to import, and why the rest is left out. */
data class ImportPlan(
    val products: List<PlannedProduct>,
    val inList: Int,
    val skippedType: Int,
    val skippedMarket: Int,
    val skippedNoSet: Int,
)

/**
 * Turns Cardmarket's product list into products for the shop. Cardmarket lists every edition in one file without
 * a language, so a product is only kept when its expansion clearly belongs to a set of our catalog, which holds
 * the English sets. Japanese, Chinese and other editions mostly have expansions of their own, and their names
 * usually say so too.
 */
object CardmarketProductMapper {

    /** Cardmarket's number for each game, as in `products_nonsingles_6.json`. */
    fun gameId(game: Game): Int = when (game) {
        Game.POKEMON -> 6
        Game.MTG -> 1
        Game.ONE_PIECE -> 18
        Game.LORCANA -> 19
    }

    fun parse(json: JsonNode): List<CardmarketProduct> =
        json.path("products").mapNotNull { node ->
            val id = node.path("idProduct").asLong(0)
            val name = shopName(node.path("name").asText("").replace("\"\"", "\"").trim())
            val expansion = node.path("idExpansion").asLong(0)
            if (id == 0L || name.isEmpty() || expansion == 0L) {
                null
            } else {
                CardmarketProduct(id, name, node.path("categoryName").asText(""), expansion)
            }
        }

    /**
     * The name as shops say it. Cardmarket calls a box of 18 boosters "Booster Box (18 Boosters)", next to the
     * "Booster Box" of 36, and shops call it a half booster box.
     */
    fun shopName(name: String): String = name.replace(HALF_BOOSTER_BOX, "Half Booster Box")

    /** Null for things the shop doesn't sell as a product type, such as coins, lots and complete card sets. */
    fun typeOf(category: String, name: String): ProductType? {
        val lowerName = name.lowercase()
        return when (category.lowercase()) {
            "pokémon booster", "magic booster", "one piece booster", "lorcana booster" ->
                if ("sleeved booster" in lowerName) ProductType.SLEEVED_BOOSTER else ProductType.BOOSTER_PACK
            "pokémon display" ->
                if ("booster bundle" in lowerName) ProductType.BOOSTER_BUNDLE else ProductType.BOOSTER_BOX
            "magic display", "one piece booster boxes", "lorcana booster boxes" -> ProductType.BOOSTER_BOX
            "pokémon elite trainer boxes" -> ProductType.ELITE_TRAINER_BOX
            "magic fatpack" -> ProductType.BOOSTER_BUNDLE
            "pokémon box set", "lorcana box set", "lorcana gift set" -> ProductType.COLLECTION_BOX
            "pokémon tins" -> ProductType.TIN
            "pokémon blisters" -> ProductType.BLISTER
            "pokémon theme decks", "pokémon trainer kits", "magic intropack", "magic starter deck",
            "magic tournamentpack", "one piece preconstructed decks", "lorcana starter decks" -> ProductType.STARTER_DECK
            else -> null
        }
    }

    /**
     * False for what a Dutch shop can't get from a European distributor: shop exclusives from elsewhere, Asian
     * editions, promos, and cases or displays that hold several of another product.
     */
    fun soldInEurope(name: String, type: ProductType): Boolean {
        val words = " ${CatalogText.nameSearch(name)} "
        if (NOT_IN_EUROPE.any { " $it " in words }) return false
        // A display of bundles or blisters is a carton of another product. A booster display is a booster box.
        if (words.trimEnd().endsWith(" display") && type != ProductType.BOOSTER_BOX) return false
        return true
    }

    fun plan(products: List<CardmarketProduct>, sets: List<CatalogSetName>): ImportPlan {
        var skippedType = 0
        var skippedMarket = 0
        val kept = products.mapNotNull { product ->
            val type = typeOf(product.category, product.name)
            when {
                type == null -> {
                    skippedType++
                    null
                }
                !soldInEurope(product.name, type) -> {
                    skippedMarket++
                    null
                }
                else -> product to type
            }
        }

        val setNames = sets.map { CatalogText.nameSearch(it.name) to it.id }.filter { it.first.isNotEmpty() }
        val setOfExpansion = kept.groupBy { it.first.expansionId }.mapValues { (_, inExpansion) ->
            val votes = inExpansion.mapNotNull { matchSet(it.first.name, setNames) }.groupingBy { it }.eachCount()
            val (setId, count) = votes.maxByOrNull { it.value } ?: return@mapValues null
            // Most of the expansion has to agree. A bag of loose products under one expansion stays out.
            setId.takeIf { count * 2 >= inExpansion.size }
        }

        val planned = kept.mapNotNull { (product, type) ->
            setOfExpansion[product.expansionId]?.let { PlannedProduct(product, type, it) }
        }
        return ImportPlan(
            products = planned,
            inList = products.size,
            skippedType = skippedType,
            skippedMarket = skippedMarket,
            skippedNoSet = kept.size - planned.size,
        )
    }

    /** The set whose name the product name starts with. The longest wins, so "Base Set 2" beats "Base Set". */
    private fun matchSet(productName: String, setNames: List<Pair<String, Long>>): Long? {
        val name = CatalogText.nameSearch(productName)
        return setNames
            .filter { (setName) -> name == setName || name.startsWith("$setName ") }
            .maxByOrNull { (setName) -> setName.length }
            ?.second
    }

    private val HALF_BOOSTER_BOX = Regex("""\bBooster Box \(18 Boosters\)""", RegexOption.IGNORE_CASE)

    /** Whole words, in `nameSearch` form. */
    private val NOT_IN_EUROPE = listOf(
        "pokemon center", "jp", "japanese", "korean", "kr", "chinese", "s ch", "t ch", "simplified", "traditional",
        "indonesian", "thai", "id th", "asia", "asian", "costco", "walmart", "target", "best buy", "gamestop",
        "sam s club", "1 card", "empty", "case", "promo", "prerelease", "judge", "participation", "event pack",
    )
}
