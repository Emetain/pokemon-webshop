package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.PremiumPlan
import com.emetain.pokemonwebshop.service.PremiumReason
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

data class PremiumCheckoutRequest(val plan: PremiumPlan, val consent: Boolean = false)

data class RedirectDto(val url: String)

/** Gives an account Premium from admin (2026-11-21). [lastDay] null: for good; otherwise until and including that day. */
data class PremiumGrantRequest(
    @field:NotBlank(message = "Vul een e-mailadres in.")
    @field:Size(max = 254, message = "Dit e-mailadres is te lang.")
    val email: String = "",
    val lastDay: LocalDate? = null,
)

/** Someone with Premium, for the admin page: given (for good or until a day) or paid. */
data class PremiumMemberDto(
    val id: Long,
    val email: String,
    val displayName: String,
    val reason: PremiumReason,
    val since: Instant?,
    /** For a gift with an end: the last day it lasts. For a subscription: until when it's paid. */
    val lastDay: LocalDate?,
    /** Given Premium can be taken back here; a paid subscription is cancelled in Stripe. */
    val canRevoke: Boolean,
)
