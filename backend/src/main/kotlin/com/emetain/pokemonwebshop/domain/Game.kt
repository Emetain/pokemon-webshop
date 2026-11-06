package com.emetain.pokemonwebshop.domain

/** The games the shop carries. [slug] is the name of the game in URLs. */
enum class Game(val slug: String) {
    POKEMON("pokemon"),
    LORCANA("lorcana"),
    MTG("magic"),
    ONE_PIECE("one-piece"),
    ;

    companion object {
        fun fromSlug(slug: String): Game? = entries.firstOrNull { it.slug == slug }
    }
}
