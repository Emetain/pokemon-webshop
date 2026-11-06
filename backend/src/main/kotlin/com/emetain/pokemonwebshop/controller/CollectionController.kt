package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AddCollectionEntryRequest
import com.emetain.pokemonwebshop.dto.BulkAddRequest
import com.emetain.pokemonwebshop.dto.BulkAddResultDto
import com.emetain.pokemonwebshop.dto.CardCollectionDto
import com.emetain.pokemonwebshop.dto.CollectionChangeDto
import com.emetain.pokemonwebshop.dto.CollectionHistoryDto
import com.emetain.pokemonwebshop.dto.CollectionOverviewDto
import com.emetain.pokemonwebshop.dto.SetProgressDto
import com.emetain.pokemonwebshop.dto.StartedSetDto
import com.emetain.pokemonwebshop.dto.UpdateCollectionEntryRequest
import com.emetain.pokemonwebshop.dto.UserDto
import com.emetain.pokemonwebshop.orchestration.CollectionOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RequiresLogin
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/collection")
@RequiresLogin
class CollectionController(
    private val collectionOrchestrator: CollectionOrchestrator,
) {

    @GetMapping
    fun overview(@CurrentUser user: User): CollectionOverviewDto =
        collectionOrchestrator.overview(user)

    @GetMapping("/history")
    fun history(@CurrentUser user: User): CollectionHistoryDto =
        collectionOrchestrator.history(user)

    @GetMapping("/{game}/sets")
    fun startedSets(@CurrentUser user: User, @PathVariable game: String): List<StartedSetDto> =
        collectionOrchestrator.startedSets(user, game)

    @GetMapping("/{game}/sets/{code}")
    fun set(@CurrentUser user: User, @PathVariable game: String, @PathVariable code: String): SetProgressDto =
        collectionOrchestrator.set(user, game, code)

    @GetMapping("/cards/{cardId}")
    fun card(@CurrentUser user: User, @PathVariable cardId: Long): CardCollectionDto =
        collectionOrchestrator.card(user, cardId)

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    fun addEntry(@CurrentUser user: User, @RequestBody request: AddCollectionEntryRequest): CollectionChangeDto =
        collectionOrchestrator.addEntry(user, request)

    @PostMapping("/entries/bulk")
    fun addBulk(@CurrentUser user: User, @Valid @RequestBody request: BulkAddRequest): BulkAddResultDto =
        collectionOrchestrator.addBulk(user, request)

    @PutMapping("/entries/{id}")
    fun updateEntry(
        @CurrentUser user: User,
        @PathVariable id: Long,
        @RequestBody request: UpdateCollectionEntryRequest,
    ): CollectionChangeDto = collectionOrchestrator.updateEntry(user, id, request)

    @DeleteMapping("/entries/{id}")
    fun removeEntry(@CurrentUser user: User, @PathVariable id: Long): CollectionChangeDto =
        collectionOrchestrator.removeEntry(user, id)

    @PutMapping("/share")
    fun share(@CurrentUser user: User): UserDto =
        collectionOrchestrator.share(user)

    @DeleteMapping("/share")
    fun stopSharing(@CurrentUser user: User): UserDto =
        collectionOrchestrator.stopSharing(user)
}
