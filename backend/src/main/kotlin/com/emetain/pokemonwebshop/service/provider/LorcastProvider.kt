package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Game
import org.springframework.stereotype.Component
import java.time.Clock

/** Disney Lorcana from Lorcast. */
@Component
class LorcastProvider(
    private val http: CatalogHttpClient,
    private val clock: Clock,
    properties: AppProperties,
) : CardDataProvider {

    private val source = properties.catalog.lorcast

    override val game = Game.LORCANA

    override fun fetchCatalog(onSet: (ProviderSet) -> Unit) {
        http.getJson("${source.baseUrl}/sets", source.requestDelay).path("results").forEachIndexed { index, set ->
            val cards = http.getJson("${source.baseUrl}/sets/${set.requireText("code")}/cards", source.requestDelay)
            val fetchedAt = clock.instant()
            onSet(LorcastMapper.set(set, sortOrder = index, cards.map { LorcastMapper.card(it, fetchedAt) }))
        }
    }
}
