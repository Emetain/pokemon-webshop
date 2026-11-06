package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.BinderCardDto
import com.emetain.pokemonwebshop.dto.BinderPageDto
import com.emetain.pokemonwebshop.service.BinderService
import org.springframework.stereotype.Component

@Component
class BinderOrchestrator(
    private val binderService: BinderService,
) {

    fun page(viewer: User?, page: Int): BinderPageDto = binderService.page(viewer, page)

    fun selection(user: User): List<BinderCardDto> = binderService.selection(user.id)

    fun select(user: User, cardId: Long) = retryingOnConflict { binderService.select(user, cardId) }

    fun deselect(user: User, cardId: Long) = binderService.deselect(user.id, cardId)
}
