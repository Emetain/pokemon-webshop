package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.DiscountCode
import com.emetain.pokemonwebshop.domain.DiscountKind
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.ProductType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/** What a discount code does, without the bookkeeping of its uses. */
data class DiscountTerms(
    val kind: DiscountKind,
    val percent: Int? = null,
    val amountCents: Long? = null,
    val minimumOrderCents: Long? = null,
    val games: Set<Game> = emptySet(),
    val productTypes: Set<ProductType> = emptySet(),
    val appliesToDrops: Boolean = false,
) {
    companion object {
        fun of(code: DiscountCode) = DiscountTerms(
            kind = code.kind,
            percent = code.percent,
            amountCents = code.amountCents,
            minimumOrderCents = code.minimumOrderCents,
            games = code.games.toSet(),
            productTypes = code.productTypes.toSet(),
            appliesToDrops = code.appliesToDrops,
        )
    }
}

/** A line of an order or cart, at the price it is sold for: sale price or drop price included. */
data class DiscountLine(val unitPriceCents: Long, val quantity: Int, val game: Game?, val type: ProductType) {
    val totalCents: Long get() = unitPriceCents * quantity
}

/** Why a code doesn't apply. */
enum class DiscountProblem {
    UNKNOWN,
    INACTIVE,
    NOT_STARTED,
    EXPIRED,
    USED_UP,
    ALREADY_USED,
    BELOW_MINIMUM,
    NO_MATCHING_PRODUCTS,
    NOT_FOR_DROPS,
}

/** The rules of discount codes. Pure, so every combination can be tested without a database. */
object DiscountRules {

    /** Whether the code counts for this line: its game and kind of product, when the code names any. */
    fun matches(terms: DiscountTerms, line: DiscountLine): Boolean =
        (terms.games.isEmpty() || line.game in terms.games) &&
            (terms.productTypes.isEmpty() || line.type in terms.productTypes)

    /** What the products the code counts for cost together. */
    fun eligibleCents(terms: DiscountTerms, lines: List<DiscountLine>): Long =
        lines.filter { matches(terms, it) }.sumOf { it.totalCents }

    /**
     * What the code takes off: a percentage of the products it counts for, rounded half up to a cent, or a fixed
     * amount. Never more than those products cost, so it can't make shipping cheaper or an order negative.
     */
    fun discountCents(terms: DiscountTerms, lines: List<DiscountLine>): Long {
        val eligible = eligibleCents(terms, lines)
        val raw = when (terms.kind) {
            DiscountKind.PERCENTAGE -> BigDecimal.valueOf(eligible)
                .multiply(BigDecimal.valueOf((terms.percent ?: 0).toLong()))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .longValueExact()
            DiscountKind.FIXED_AMOUNT -> terms.amountCents ?: 0
        }
        return raw.coerceIn(0, eligible)
    }

    /** What about the order keeps the code from working. The minimum goes by all products, before the discount. */
    fun orderProblem(terms: DiscountTerms, lines: List<DiscountLine>, drop: Boolean): DiscountProblem? = when {
        drop && !terms.appliesToDrops -> DiscountProblem.NOT_FOR_DROPS
        terms.minimumOrderCents != null && lines.sumOf { it.totalCents } < terms.minimumOrderCents ->
            DiscountProblem.BELOW_MINIMUM
        eligibleCents(terms, lines) <= 0 -> DiscountProblem.NO_MATCHING_PRODUCTS
        else -> null
    }

    fun timeProblem(active: Boolean, startsAt: Instant?, endsAt: Instant?, now: Instant): DiscountProblem? = when {
        !active -> DiscountProblem.INACTIVE
        startsAt != null && now.isBefore(startsAt) -> DiscountProblem.NOT_STARTED
        endsAt != null && !now.isBefore(endsAt) -> DiscountProblem.EXPIRED
        else -> null
    }

    /** [uses] counts every order with the code that isn't cancelled, also those still waiting for payment. */
    fun usageProblem(maxUses: Int?, uses: Long, oncePerCustomer: Boolean, usesByCustomer: Long): DiscountProblem? = when {
        maxUses != null && uses >= maxUses -> DiscountProblem.USED_UP
        oncePerCustomer && usesByCustomer > 0 -> DiscountProblem.ALREADY_USED
        else -> null
    }

    /** What the customer reads. Codes that can't be used now all say the same, so nobody can guess codes. */
    fun message(problem: DiscountProblem, terms: DiscountTerms?): String = when (problem) {
        DiscountProblem.UNKNOWN, DiscountProblem.INACTIVE, DiscountProblem.NOT_STARTED, DiscountProblem.EXPIRED,
        DiscountProblem.USED_UP -> "Deze kortingscode is niet (meer) geldig."
        DiscountProblem.ALREADY_USED -> "Je hebt deze kortingscode al gebruikt."
        DiscountProblem.BELOW_MINIMUM ->
            "Deze kortingscode geldt vanaf ${Money.format(terms?.minimumOrderCents ?: 0)} aan producten."
        DiscountProblem.NO_MATCHING_PRODUCTS -> "Deze kortingscode geldt niet voor de producten in je bestelling."
        DiscountProblem.NOT_FOR_DROPS -> "Deze kortingscode geldt niet voor drops."
    }

    /** Spaces and case don't matter: " welkom10 " is WELKOM10. */
    fun normalize(code: String): String = code.trim().uppercase().replace(WHITESPACE, "")

    fun isValidCode(code: String): Boolean = CODE.matches(code)

    private val HUNDRED = BigDecimal.valueOf(100)
    private val WHITESPACE = Regex("\\s+")
    private val CODE = Regex("[A-Z0-9_-]{3,40}")
}
