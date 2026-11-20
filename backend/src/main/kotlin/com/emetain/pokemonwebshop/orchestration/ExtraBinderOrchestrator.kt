package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.BinderDetailDto
import com.emetain.pokemonwebshop.dto.BinderEntryRequest
import com.emetain.pokemonwebshop.dto.BinderListDto
import com.emetain.pokemonwebshop.dto.BinderRefDto
import com.emetain.pokemonwebshop.dto.BinderSummaryDto
import com.emetain.pokemonwebshop.dto.CardBinderEntryDto
import com.emetain.pokemonwebshop.dto.CardBindersDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.ExtraBinderRepository
import com.emetain.pokemonwebshop.service.ExtraBinderService
import com.emetain.pokemonwebshop.service.PremiumService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Extra binders (Premium, phase 12). Reading them always works; changing them needs Premium. */
@Component
class ExtraBinderOrchestrator(
    private val binderService: ExtraBinderService,
    private val binderRepository: ExtraBinderRepository,
    private val premiumService: PremiumService,
    properties: AppProperties,
) {
    private val max = properties.premium.maxBinders

    fun list(user: User): BinderListDto =
        BinderListDto(premiumService.has(user), max, binderService.list(user.id, user.currency).map(BinderSummaryDto::from))

    fun detail(user: User, binderId: Long): BinderDetailDto =
        BinderDetailDto.from(binderService.contents(user.id, binderId, user.currency), premiumService.has(user))

    fun create(user: User, name: String?): BinderDetailDto {
        val binder = binderService.create(user, name ?: throw ApiException.validation("Geef je binder een naam."))
        return detail(user, binder.id)
    }

    fun rename(user: User, binderId: Long, name: String?): BinderDetailDto {
        binderService.rename(user, binderId, name ?: throw ApiException.validation("Geef je binder een naam."))
        return detail(user, binderId)
    }

    fun delete(user: User, binderId: Long) = binderService.delete(user, binderId)

    fun add(user: User, binderId: Long, request: BinderEntryRequest): CardBindersDto {
        val cardId = request.cardId ?: throw ApiException.validation("Kies een kaart.")
        binderService.add(user, binderId, cardId, request.variant, request.quantity)
        return card(user, cardId)
    }

    fun setQuantity(user: User, binderId: Long, entryId: Long, quantity: Int): BinderDetailDto {
        binderService.setQuantity(user, binderId, entryId, quantity)
        return detail(user, binderId)
    }

    /** The customer's binders and which of them hold this card, for the panel on the card page. */
    @Transactional(readOnly = true)
    fun card(user: User, cardId: Long): CardBindersDto = CardBindersDto(
        binders = binderRepository.findAllByUserIdOrderByCreatedAtAscIdAsc(user.id).map(BinderRefDto::from),
        entries = binderService.entriesOfCard(user.id, cardId).map(CardBinderEntryDto::from),
    )
}
