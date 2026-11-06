package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.SharedCollectionDto
import com.emetain.pokemonwebshop.orchestration.CollectionOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Public: anyone with the link can see a collection its owner chose to share. */
@RestController
@RequestMapping("/v1/shared-collections")
class SharedCollectionController(
    private val collectionOrchestrator: CollectionOrchestrator,
) {

    @GetMapping("/{slug}")
    fun shared(@PathVariable slug: String, @CurrentUser viewer: User?): SharedCollectionDto =
        collectionOrchestrator.shared(slug, viewer)
}
