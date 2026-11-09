package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

/** Gives back the stock of checkouts that weren't paid in time, and clears out old guest carts. */
@Component
class CheckoutJobs(
    private val orderRepository: OrderRepository,
    private val orderPaymentService: OrderPaymentService,
    private val cartService: CartService,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Every minute by default. One order failing doesn't stop the others. Returns how many went back. */
    @Scheduled(cron = "\${app.checkout.release-cron}", zone = "Europe/Amsterdam")
    fun releaseExpiredReservations(): Int {
        val minutes = properties.checkout.reservationTtl.toMinutes()
        var released = 0
        for (orderId in orderRepository.findExpiredReservations(clock.instant())) {
            try {
                if (orderPaymentService.closePending(orderId, "Niet binnen $minutes minuten betaald")) released++
            } catch (exception: RuntimeException) {
                log.error("Could not release the reservation of order {}", orderId, exception)
            }
        }
        if (released > 0) log.info("Released the stock of {} unpaid checkouts", released)
        return released
    }

    @Scheduled(cron = "\${app.checkout.cart-cleanup-cron}", zone = "Europe/Amsterdam")
    fun deleteOldGuestCarts() {
        val deleted = cartService.deleteGuestCartsOlderThan(properties.checkout.guestCartTtl)
        if (deleted > 0) log.info("Deleted {} guest carts", deleted)
    }
}
