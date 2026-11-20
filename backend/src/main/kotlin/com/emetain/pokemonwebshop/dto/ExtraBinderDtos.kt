package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.ExtraBinder
import com.emetain.pokemonwebshop.domain.ExtraBinderEntry
import com.emetain.pokemonwebshop.service.BinderContents
import com.emetain.pokemonwebshop.service.BinderSummary
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class BinderRequest(
    @field:NotNull(message = "Geef je binder een naam.")
    @field:Size(max = 200, message = "De naam mag maximaal 60 tekens lang zijn.")
    val name: String?,
)

data class BinderEntryRequest(
    @field:NotNull(message = "Kies een kaart.")
    val cardId: Long?,
    val variant: String? = null,
    val quantity: Int = 1,
)

data class BinderQuantityRequest(val quantity: Int)

data class BinderSummaryDto(
    val id: Long,
    val name: String,
    val copies: Long,
    val valueCents: Long,
    val currency: Currency,
    val unpricedCards: Long,
) {
    companion object {
        fun from(summary: BinderSummary) = BinderSummaryDto(
            id = summary.binder.id,
            name = summary.binder.name,
            copies = summary.copies,
            valueCents = summary.value.cents,
            currency = summary.value.currency,
            unpricedCards = summary.value.unpricedCards,
        )
    }
}

/** The binders, and whether they can be changed: only with Premium; without it they stay visible. */
data class BinderListDto(val premium: Boolean, val max: Int, val binders: List<BinderSummaryDto>)

data class BinderEntryDto(
    val id: Long,
    val card: CardRefDto,
    val variant: String,
    val quantity: Int,
    val priceCents: Long?,
    val priceCurrency: Currency?,
)

data class BinderDetailDto(
    val id: Long,
    val name: String,
    val premium: Boolean,
    val valueCents: Long,
    val currency: Currency,
    val unpricedCards: Long,
    val entries: List<BinderEntryDto>,
) {
    companion object {
        fun from(contents: BinderContents, premium: Boolean) = BinderDetailDto(
            id = contents.binder.id,
            name = contents.binder.name,
            premium = premium,
            valueCents = contents.value.cents,
            currency = contents.value.currency,
            unpricedCards = contents.value.unpricedCards,
            entries = contents.entries.map { entry ->
                val price = contents.prices[entry.card.id to entry.variant]
                BinderEntryDto(entry.id, CardRefDto.from(entry.card), entry.variant, entry.quantity, price?.cents, price?.currency)
            },
        )
    }
}

/** Which binders hold a card, for the card page. */
data class CardBinderEntryDto(val entryId: Long, val binderId: Long, val binderName: String, val variant: String, val quantity: Int) {
    companion object {
        fun from(entry: ExtraBinderEntry) =
            CardBinderEntryDto(entry.id, entry.binder.id, entry.binder.name, entry.variant, entry.quantity)
    }
}

data class CardBindersDto(val binders: List<BinderRefDto>, val entries: List<CardBinderEntryDto>)

data class BinderRefDto(val id: Long, val name: String) {
    companion object {
        fun from(binder: ExtraBinder) = BinderRefDto(binder.id, binder.name)
    }
}
