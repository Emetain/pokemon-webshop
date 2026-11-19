package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.PremiumPlan

data class PremiumCheckoutRequest(val plan: PremiumPlan, val consent: Boolean = false)

data class RedirectDto(val url: String)
