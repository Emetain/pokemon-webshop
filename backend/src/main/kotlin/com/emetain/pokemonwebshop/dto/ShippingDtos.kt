package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.ShippingRate
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class ShippingRateDto(
    val country: String,
    val priceCents: Long,
    val freeFromCents: Long?,
    val deliveryTime: String,
    val active: Boolean,
    val updatedAt: Instant,
) {
    companion object {
        fun from(rate: ShippingRate) = ShippingRateDto(
            country = rate.country,
            priceCents = rate.priceCents,
            freeFromCents = rate.freeFromCents,
            deliveryTime = rate.deliveryTime,
            active = rate.active,
            updatedAt = rate.updatedAt,
        )
    }
}

data class ShippingRateRequest(
    @field:Min(0, message = "Verzendkosten kunnen niet negatief zijn.")
    @field:Max(100_000, message = "Dat zijn wel erg hoge verzendkosten.")
    val priceCents: Long = 0,

    /** Empty: shipping is never free. */
    val freeFromCents: Long? = null,

    @field:NotBlank(message = "Vul de levertijd in.")
    @field:Size(max = 200, message = "De levertijd mag maximaal 200 tekens lang zijn.")
    val deliveryTime: String = "",

    val active: Boolean = true,
)
