package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Game
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Riftbound from TCGplayer's catalog, through tcgcsv.com: a free daily copy of TCGplayer's API that needs no key.
 * It asks for an identifiable User-Agent and a pause between calls. A sync makes one call for the groups and two
 * per group (products and prices), about 30 in all.
 */
@Component
class RiftboundProvider(
    private val http: CatalogHttpClient,
    private val clock: Clock,
    properties: AppProperties,
) : CardDataProvider {

    private val source = properties.catalog.tcgcsv

    override val game = Game.RIFTBOUND

    override fun fetchCatalog(onSet: (ProviderSet) -> Unit) {
        val base = "${source.baseUrl}/tcgplayer/$CATEGORY"
        val groups = http.getJson("$base/groups", source.requestDelay).path("results").toList()
        val fetchedAt = clock.instant()
        val withCards = groups.map { group ->
            val id = group.requireText("groupId")
            RiftboundGroup(
                group = group,
                products = http.getJson("$base/$id/products", source.requestDelay).path("results").toList(),
                prices = http.getJson("$base/$id/prices", source.requestDelay).path("results").toList(),
            )
        }
        RiftboundMapper.sets(withCards, fetchedAt).forEach(onSet)
    }

    private companion object {
        /** TCGplayer's category for "Riftbound: League of Legends Trading Card Game". */
        const val CATEGORY = 89
    }
}
