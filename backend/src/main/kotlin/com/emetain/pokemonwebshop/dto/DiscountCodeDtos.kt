package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.DiscountCode
import com.emetain.pokemonwebshop.service.DiscountRules
import com.emetain.pokemonwebshop.service.DiscountUsage
import jakarta.validation.constraints.Size
import java.time.Instant

/** A discount code as the admin manages it, with how often it was used. */
data class AdminDiscountCodeDto(
    val id: Long,
    val code: String,
    /** `PERCENTAGE` or `FIXED_AMOUNT`. */
    val kind: String,
    val percent: Int?,
    val amountCents: Long?,
    val minimumOrderCents: Long?,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val maxUses: Int?,
    val oncePerCustomer: Boolean,
    val appliesToDrops: Boolean,
    val active: Boolean,
    val note: String?,
    val games: List<String>,
    val productTypes: List<String>,
    /** Orders with the code that aren't cancelled, also those waiting for payment. */
    val uses: Long,
    val paidUses: Long,
    val givenCents: Long,
    /** `ACTIVE`, `OFF`, `SCHEDULED`, `EXPIRED` or `USED_UP`. */
    val state: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(code: DiscountCode, usage: DiscountUsage?, now: Instant): AdminDiscountCodeDto {
            val uses = usage?.uses ?: 0
            val timeProblem = DiscountRules.timeProblem(code.active, code.startsAt, code.endsAt, now)
            val state = when {
                !code.active -> "OFF"
                timeProblem != null -> timeProblem.name.let { if (it == "NOT_STARTED") "SCHEDULED" else "EXPIRED" }
                code.maxUses != null && uses >= code.maxUses!! -> "USED_UP"
                else -> "ACTIVE"
            }
            return AdminDiscountCodeDto(
                id = code.id,
                code = code.code,
                kind = code.kind.name,
                percent = code.percent,
                amountCents = code.amountCents,
                minimumOrderCents = code.minimumOrderCents,
                startsAt = code.startsAt,
                endsAt = code.endsAt,
                maxUses = code.maxUses,
                oncePerCustomer = code.oncePerCustomer,
                appliesToDrops = code.appliesToDrops,
                active = code.active,
                note = code.note,
                games = code.games.map { it.slug }.sorted(),
                productTypes = code.productTypes.map { it.name }.sorted(),
                uses = uses,
                paidUses = usage?.paidUses ?: 0,
                givenCents = usage?.givenCents ?: 0,
                state = state,
                createdAt = code.createdAt,
            )
        }
    }
}

data class DiscountCodeRequest(
    @field:Size(max = 60, message = "Een code heeft maximaal 40 tekens.")
    val code: String = "",
    val kind: String = "PERCENTAGE",
    val percent: Int? = null,
    val amountCents: Long? = null,
    val minimumOrderCents: Long? = null,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
    val maxUses: Int? = null,
    val oncePerCustomer: Boolean = false,
    val appliesToDrops: Boolean = false,
    val active: Boolean = true,
    @field:Size(max = 200, message = "De notitie mag maximaal 200 tekens hebben.")
    val note: String? = null,
    /** Game slugs. Empty is every game. */
    val games: List<String> = emptyList(),
    /** Product types. Empty is every kind of product. */
    val productTypes: List<String> = emptyList(),
)

/** A discount code in the cart or checkout: what it takes off, or why it doesn't apply. */
data class CartDiscountDto(
    val code: String,
    val amountCents: Long,
    val applied: Boolean,
    val message: String?,
)
