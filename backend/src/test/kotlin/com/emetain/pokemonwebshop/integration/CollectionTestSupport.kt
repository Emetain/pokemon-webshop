package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.service.CatalogImportService
import com.emetain.pokemonwebshop.service.provider.ProviderCard
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Imports cards like a sync does, and adds them to a collection like the site does. */
abstract class CollectionTestSupport : IntegrationTest() {

    @Autowired
    protected lateinit var importService: CatalogImportService

    @Autowired
    protected lateinit var cardRepository: CardRepository

    @Autowired
    protected lateinit var cardSetRepository: CardSetRepository

    protected fun importCards(cards: List<ProviderCard>, game: Game = Game.POKEMON, code: String = uniqueCode()): String {
        importService.importSet(game, providerSet(code, cards))
        return code
    }

    protected fun card(externalId: String, game: Game = Game.POKEMON): Card =
        cardRepository.findAllByGameAndExternalIdIn(game, listOf(externalId)).single()

    protected fun add(session: Cookie, cardId: Long, variant: String? = null, quantity: Int = 1): ResultActions =
        mockMvc.perform(
            post("/v1/collection/entries").cookie(session)
                .json(mapOf("cardId" to cardId, "variant" to variant, "quantity" to quantity)),
        ).andExpect(status().isCreated)

    protected fun ResultActions.entryId(variant: String): Long =
        objectMapper.readTree(andReturn().response.contentAsString)["entries"].first { it["variant"].asText() == variant }["id"].asLong()
}
