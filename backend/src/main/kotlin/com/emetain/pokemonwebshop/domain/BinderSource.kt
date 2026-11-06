package com.emetain.pokemonwebshop.domain

/** What the binder on the home page shows. */
enum class BinderSource {
    POPULAR,
    OWN_SELECTION,
    MOST_EXPENSIVE_OWNED,
    POKEDEX,
    MOST_EXPENSIVE,
    ;

    companion object {
        /** Logged-in customers see their own best cards until they choose something else. */
        val DEFAULT = MOST_EXPENSIVE_OWNED
    }
}
