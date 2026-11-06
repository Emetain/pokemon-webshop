package com.emetain.pokemonwebshop.service.provider

import com.fasterxml.jackson.databind.JsonNode
import java.math.RoundingMode

/** Turns a price in euros or dollars into cents. Anything that isn't a real, positive price becomes null. */
object PriceCents {

    // Sources write 0 for "no price". Far above any real card, this keeps absurd values out of the Long range.
    private const val MAX_AMOUNT = 100_000_000

    fun parse(value: JsonNode?): Long? =
        if (value == null || !value.isValueNode || value.isNull) null else parse(value.asText())

    fun parse(text: String?): Long? {
        val amount = text?.trim()?.toBigDecimalOrNull() ?: return null
        if (amount.signum() <= 0 || amount > MAX_AMOUNT.toBigDecimal()) return null
        val cents = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        return cents.takeIf { it > 0 }
    }
}
