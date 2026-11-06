package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Game
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component

/**
 * One Piece from the OPTCG API. One person pays for this server and asks to keep the number of calls low,
 * so a full sync uses six calls and the cards are sorted into sets here.
 */
@Component
class OptcgProvider(
    private val http: CatalogHttpClient,
    properties: AppProperties,
) : CardDataProvider {

    private val source = properties.catalog.optcg

    override val game = Game.ONE_PIECE

    override fun fetchCatalog(onSet: (ProviderSet) -> Unit) {
        val lists = OptcgLists(
            sets = list("/allSets/"),
            decks = list("/allDecks/"),
            setCards = list("/allSetCards/"),
            deckCards = list("/allSTCards/"),
            // The documented /allPromoCards/ answers 404.
            promoCards = list("/promos/filtered/?rarity=P"),
            donCards = list("/allDonCards/"),
        )
        OptcgMapper.sets(lists).forEach(onSet)
    }

    private fun list(path: String): List<JsonNode> =
        http.getJson("${source.baseUrl}$path", source.requestDelay).toList()
}
