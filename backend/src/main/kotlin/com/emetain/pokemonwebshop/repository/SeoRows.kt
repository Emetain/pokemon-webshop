package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.ProductType
import java.time.Instant

/** A page for the sitemap: its slug and when it last changed. */
data class SlugRow(val slug: String, val updatedAt: Instant)

data class GameTypeRow(val game: Game, val type: ProductType)

data class SetRow(val game: Game, val code: String)
