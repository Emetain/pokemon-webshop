package com.emetain.pokemonwebshop.service

import java.security.SecureRandom
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** How many people the next batch of the waiting room lets through, and up to when that uses the admission rate. */
data class AdmissionBatch(val size: Int, val usedUntil: Instant)

/** What a customer has towards the limits of a drop, and what they want to have. */
data class DropLimitCheck(
    /** Per product: the most one customer buys. Null: no limit of its own. */
    val maxPerProduct: Int?,
    /** All products together. Null: no limit for the whole drop. */
    val maxPerDrop: Int?,
    /** Of this product, in orders that count. */
    val orderedProduct: Int,
    /** All products, in orders that count. */
    val orderedDrop: Int,
    /** Of this product, what the customer wants after the change: in the cart, or going to checkout. */
    val wantedProduct: Int,
    /** All products, in the cart or the checkout after the change. */
    val wantedDrop: Int,
)

/** Which limit a quantity would break. */
enum class DropLimitProblem { PRODUCT, DROP }

/** The rules of a drop. Pure, so they can be tested without a database. */
object DropRules {

    /** At most this many products in one drop: it keeps the drop page and the payment page readable. */
    const val MAX_PRODUCTS = 30

    private val random: Random = SecureRandom().asKotlinRandom()

    /**
     * The waiting room in a random order, with a cryptographic random generator: nobody can predict it, and
     * joining at the last second gives no better place than joining half an hour early.
     */
    fun <T> shuffle(waiting: List<T>, random: Random = this.random): List<T> = waiting.shuffled(random)

    /**
     * The batch for [now], at [perMinute] people a minute. The rate is used up to [usedUntil] and moves forward by
     * whole people only, so the speed stays exact: 5 a minute lets one through every 12 seconds, not one every batch.
     * After a pause or a quiet moment, at most two batches' worth catches up at once.
     */
    fun admissionBatch(perMinute: Int, usedUntil: Instant?, now: Instant, interval: Duration): AdmissionBatch {
        if (perMinute <= 0) return AdmissionBatch(0, now)
        val millisPerPerson = MILLIS_PER_MINUTE.toDouble() / perMinute
        val earliest = now.minus(interval.multipliedBy(2))
        val from = usedUntil?.takeIf { it.isAfter(earliest) } ?: earliest
        val elapsed = Duration.between(from, now).toMillis().coerceAtLeast(0)
        val size = (elapsed / millisPerPerson).toInt()
        return AdmissionBatch(size, from.plusMillis((size * millisPerPerson).toLong()))
    }

    /** Seconds until someone with [ahead] people before them gets in, at [perMinute] a minute in batches. */
    fun estimatedWaitSeconds(ahead: Long, perMinute: Int, interval: Duration): Long? {
        if (perMinute <= 0) return null
        val seconds = Math.ceil((ahead + 1) * SECONDS_PER_MINUTE / perMinute.toDouble()).toLong()
        // Nobody gets in sooner than the next batch.
        return maxOf(seconds, interval.toSeconds())
    }

    /** Null when the quantities fit both limits. */
    fun limitProblem(check: DropLimitCheck): DropLimitProblem? = when {
        check.maxPerProduct != null && check.orderedProduct + check.wantedProduct > check.maxPerProduct -> DropLimitProblem.PRODUCT
        check.maxPerDrop != null && check.orderedDrop + check.wantedDrop > check.maxPerDrop -> DropLimitProblem.DROP
        else -> null
    }

    /**
     * The most of one product that fits both limits, given what was ordered and what else is in the cart. Null: no
     * limit. [inCartOther] is the drop cart without this product.
     */
    fun roomLeft(maxPerProduct: Int?, maxPerDrop: Int?, orderedProduct: Int, orderedDrop: Int, inCartOther: Int): Int? {
        val forProduct = maxPerProduct?.let { it - orderedProduct }
        val forDrop = maxPerDrop?.let { it - orderedDrop - inCartOther }
        return listOfNotNull(forProduct, forDrop).minOrNull()?.coerceAtLeast(0)
    }

    /**
     * The email as one person, for limits: lowercase, without a `+tag`, and for Gmail without dots, because Gmail
     * delivers `j.ansen+drop@gmail.com` to `jansen@gmail.com`. Other providers treat dots as real characters.
     */
    fun emailKey(email: String): String {
        val address = email.trim().lowercase(Locale.ROOT)
        val at = address.lastIndexOf('@')
        if (at <= 0) return address
        var local = address.substring(0, at).substringBefore('+')
        var domain = address.substring(at + 1)
        if (domain in GMAIL_DOMAINS) {
            local = local.replace(".", "")
            domain = "gmail.com"
        }
        return "$local@$domain".take(EMAIL_KEY_LENGTH)
    }

    /**
     * The address as one place, for limits. In the Netherlands a postcode and house number point to one address, so
     * the street can be written any way. A Belgian postcode covers a whole town, so there the street counts too.
     * "12 a", "12-A" and "12a" are the same house number.
     */
    fun addressKey(street: String, houseNumber: String, postcode: String, country: String): String {
        val code = compact(postcode)
        val number = compact(houseNumber)
        val parts = if (country == "NL") listOf(country, code, number) else listOf(country, code, compact(street), number)
        return parts.joinToString("|").take(ADDRESS_KEY_LENGTH)
    }

    /** The waiting room opens a while before the start. */
    fun waitingRoomOpensAt(startsAt: Instant, opensBefore: Duration): Instant = startsAt.minus(opensBefore)

    /** Uppercase letters and digits only, without accents. */
    private fun compact(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(ACCENTS, "")
            .uppercase(Locale.ROOT)
            .replace(NOT_ALPHANUMERIC, "")

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val SECONDS_PER_MINUTE = 60.0
    private const val EMAIL_KEY_LENGTH = 254
    private const val ADDRESS_KEY_LENGTH = 200
    private val GMAIL_DOMAINS = setOf("gmail.com", "googlemail.com")
    private val ACCENTS = Regex("\\p{M}+")
    private val NOT_ALPHANUMERIC = Regex("[^A-Z0-9]")
}
