package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceAlertKind
import com.emetain.pokemonwebshop.service.PriceAlertView
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class PriceAlertRequest(
    @field:NotNull(message = "Kies een kaart.")
    val cardId: Long?,
    val variant: String? = null,
    @field:NotNull(message = "Kies wat voor alert je wilt.")
    val kind: PriceAlertKind?,
    /** [PriceAlertKind.BELOW]: in the customer's currency. */
    val thresholdCents: Long? = null,
    /** [PriceAlertKind.CHANGE]. */
    val percent: Int? = null,
)

data class PriceAlertOffRequest(
    @field:NotNull(message = "Deze link werkt niet.")
    @field:Size(max = 200, message = "Deze link werkt niet.")
    val token: String?,
)

/** The card's name, so the page can say which alert was switched off. */
data class PriceAlertOffDto(val cardName: String)

data class PriceAlertDto(
    val id: Long,
    val card: CardRefDto,
    val variant: String,
    val kind: PriceAlertKind,
    val thresholdCents: Long?,
    val currency: Currency?,
    val percent: Int?,
    /** [PriceAlertKind.CHANGE]: the price the change is measured from. */
    val baselineCents: Long?,
    val baselineCurrency: Currency?,
    /** [PriceAlertKind.BELOW]: false while the price is under the line and the alert already went off. */
    val armed: Boolean,
    val lastTriggeredOn: LocalDate?,
    /** The trend price now, in its own currency. */
    val priceCents: Long?,
    val priceCurrency: Currency?,
) {
    companion object {
        fun from(view: PriceAlertView) = PriceAlertDto(
            id = view.alert.id,
            card = CardRefDto.from(view.alert.card),
            variant = view.alert.variant,
            kind = view.alert.kind,
            thresholdCents = view.alert.thresholdCents,
            currency = view.alert.currency,
            percent = view.alert.percent,
            baselineCents = view.alert.baselineCents,
            baselineCurrency = view.alert.baselineCurrency,
            armed = view.alert.armed,
            lastTriggeredOn = view.alert.lastTriggeredOn,
            priceCents = view.price?.cents,
            priceCurrency = view.price?.currency,
        )
    }
}

/** The alerts of the customer, and whether they're checked now: only with Premium. */
data class PriceAlertListDto(val premium: Boolean, val max: Int, val alerts: List<PriceAlertDto>)
