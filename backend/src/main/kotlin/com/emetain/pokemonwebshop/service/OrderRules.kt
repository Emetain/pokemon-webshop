package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Carrier
import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.OrderStatus.CANCELLED
import com.emetain.pokemonwebshop.domain.OrderStatus.DELIVERED
import com.emetain.pokemonwebshop.domain.OrderStatus.PAID
import com.emetain.pokemonwebshop.domain.OrderStatus.PENDING_PAYMENT
import com.emetain.pokemonwebshop.domain.OrderStatus.PROCESSING
import com.emetain.pokemonwebshop.domain.OrderStatus.REFUNDED
import com.emetain.pokemonwebshop.domain.OrderStatus.SHIPPED
import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom

/** Which status changes an admin can make. Pure, so it can be tested without a database. */
object OrderRules {

    /**
     * The statuses an admin can pick by hand. Paid only comes from the webhook, shipped comes with a track & trace
     * code, and cancelled and refunded have their own actions because they pay money back.
     */
    fun manualTargets(from: OrderStatus): Set<OrderStatus> = when (from) {
        PAID -> setOf(PROCESSING)
        PROCESSING -> setOf(PAID)
        SHIPPED -> setOf(DELIVERED)
        DELIVERED -> setOf(SHIPPED)
        PENDING_PAYMENT, CANCELLED, REFUNDED -> emptySet()
    }

    fun canShip(from: OrderStatus): Boolean = from in setOf(PAID, PROCESSING, SHIPPED)

    /** Before it leaves the shop. A paid order is paid back in full and its stock goes back. */
    fun canCancel(from: OrderStatus): Boolean = from in setOf(PENDING_PAYMENT, PAID, PROCESSING)

    /** After it left the shop, such as a return. Also for a cancelled order whose automatic refund failed. */
    fun canRefund(order: Order): Boolean =
        order.refundableCents > 0 && order.status in setOf(SHIPPED, DELIVERED, REFUNDED, CANCELLED)
}

/** Short, random order numbers without letters that look alike, such as `UT-7K3M9Q`. */
object OrderNumbers {
    private const val PREFIX = "UT-"
    private const val LENGTH = 6
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private val random = SecureRandom()

    fun generate(): String = PREFIX + (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** Accepts `ut-7k3m9q` and ` UT-7K3M9Q ` as well. */
    fun normalize(input: String): String = input.trim().uppercase()
}

/** The link to follow a parcel. */
object TrackingLinks {

    fun build(template: String?, code: String, postcode: String, country: String): String? {
        if (template.isNullOrBlank()) return null
        val encode = { value: String -> URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20") }
        return template
            .replace("{code}", encode(code.trim()))
            .replace("{postcode}", encode(postcode.replace(" ", "")))
            .replace("{country}", encode(country))
    }

    /** Only https links go in a mail to a customer. */
    fun isSafe(url: String): Boolean = runCatching {
        val uri = URI(url.trim())
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    fun carrierLabel(carrier: Carrier): String = when (carrier) {
        Carrier.POSTNL -> "PostNL"
        Carrier.DHL -> "DHL"
        Carrier.BPOST -> "bpost"
        Carrier.DPD -> "DPD"
        Carrier.OTHER -> "Vervoerder"
    }
}

/** The payment methods per country. All three report right away whether a payment succeeded. */
object PaymentMethods {
    const val IDEAL = "ideal"
    const val BANCONTACT = "bancontact"
    const val CARD = "card"

    fun forCountry(country: String): List<String> = when (country) {
        "NL" -> listOf(IDEAL, CARD)
        "BE" -> listOf(BANCONTACT, CARD)
        else -> listOf(CARD)
    }
}
