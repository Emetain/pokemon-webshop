package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.OrderRepository
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Caps the shop checkouts that wait for payment, per email address and per IP address, so one visitor can't hold the
 * whole stock with checkouts they never pay. Every checkout reserves its stock for `app.checkout.reservation-ttl`,
 * also for a guest. Drops have their own limits and don't count here.
 *
 * The email address is counted from the orders. The IP address isn't stored with an order, so this keeps the orders
 * started per IP address in memory, like the rate limits: a restart forgets them, and the stock they hold is released
 * by the usual job.
 */
@Service
class OpenCheckoutService(
    private val orderRepository: OrderRepository,
    private val properties: AppProperties,
    private val clock: Clock,
) {

    private data class Started(val orderId: Long, val at: Instant)

    private val byIp = ConcurrentHashMap<String, List<Started>>()

    /** Refuses a new checkout when [emailKey] or [clientIp] already has the maximum waiting for payment. */
    fun requireRoom(emailKey: String, clientIp: String) {
        val max = properties.checkout.maxOpenCheckouts
        val forEmail = orderRepository.countOpenShopCheckouts(emailKey)
        val forIp = byIp[clientIp]?.map { it.orderId }?.takeIf { it.isNotEmpty() }
            ?.let { orderRepository.countByIdInAndStatus(it, OrderStatus.PENDING_PAYMENT) } ?: 0L
        if (forEmail >= max || forIp >= max) throw tooMany(max, properties.checkout.reservationTtl.toMinutes())
    }

    /** Call this once the order of a checkout exists. */
    fun started(clientIp: String, orderId: Long) {
        val now = clock.instant()
        byIp.merge(clientIp, listOf(Started(orderId, now))) { current, new -> current.filterNot { it.isOld(now) } + new }
    }

    /** An order waits for payment at most the reservation time, plus the time to close its Stripe page. */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun evictOld() {
        val now = clock.instant()
        byIp.replaceAll { _, started -> started.filterNot { it.isOld(now) } }
        byIp.values.removeIf { it.isEmpty() }
    }

    private fun Started.isOld(now: Instant) = at.plus(properties.checkout.reservationTtl.multipliedBy(4)).isBefore(now)

    private fun tooMany(max: Int, minutes: Long) = ApiException(
        HttpStatus.TOO_MANY_REQUESTS,
        "too_many_open_checkouts",
        "Je hebt al $max bestellingen die op betaling wachten. Betaal of annuleer er eerst een, of probeer het over $minutes minuten opnieuw.",
    )
}
