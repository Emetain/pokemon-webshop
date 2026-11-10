package com.emetain.pokemonwebshop.domain

/**
 * Where a drop is. The admin publishes a [DRAFT] as [SCHEDULED]. The drop scheduler moves it to [LIVE] at the start
 * time and to [ENDED] at the end time, when everything is sold, or when the admin ends it early.
 */
enum class DropStatus {
    DRAFT,
    SCHEDULED,
    LIVE,
    ENDED,
    ;

    /** Visible in the shop: everything but a draft. */
    val public: Boolean get() = this != DRAFT
}

/** What happens to allocated stock that wasn't sold when a drop ends. Chosen per drop. */
enum class DropLeftover {
    /** Goes back to the normal stock of the product right away. */
    RETURN_TO_STOCK,

    /** Stays with the drop until the admin gives it back, for example for a second round. */
    KEEP,
}
