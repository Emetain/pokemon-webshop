package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.DropStatus
import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.StartCheckoutRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.DropQueueEntryRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Turns a drop cart into an order that holds drop stock. Runs under the lock on the drop: the limits per account,
 * email, address, product and drop are checked again here, with the checkouts of everyone else already counted, so
 * two checkouts at the same moment can't both pass a limit or take the last item.
 */
@Service
class DropCheckoutService(
    private val dropLocks: DropLocks,
    private val entryRepository: DropQueueEntryRepository,
    private val checkoutService: CheckoutService,
    private val dropStockService: DropStockService,
    private val dropCartService: DropCartService,
    private val properties: AppProperties,
    private val clock: Clock,
) {

    @Transactional
    fun createOrder(dropId: Long, entryId: Long, viewer: User?, request: StartCheckoutRequest): Order {
        val drop = dropLocks.lock(dropId) ?: throw ApiException.notFound("We konden deze drop niet vinden.")
        if (drop.status == DropStatus.ENDED) throw DropQueueService.ended()
        if (drop.status != DropStatus.LIVE) throw DropQueueService.notYourTurn()
        val entry = entryRepository.findById(entryId).orElseThrow { DropQueueService.notYourTurn() }
        if (entry.drop.id != drop.id) throw DropQueueService.notYourTurn()
        if (!entry.hasAccess(clock.instant())) throw DropQueueService.accessExpired()
        if (!request.acceptTerms) throw ApiException.validation("Ga akkoord met de algemene voorwaarden om te bestellen.")
        if (entry.items.isEmpty()) throw ApiException.validation("Je drop-mandje is leeg.")

        val party = checkoutService.party(viewer, request)
        val ordered = dropCartService.ordered(drop, viewer, party.emailKey, party.addressKey, entry.id)
        val orderedTotal = ordered.values.sum()
        val wantedTotal = entry.items.sumOf { it.quantity }
        for (item in entry.items) {
            val dropProduct = item.dropProduct
            val check = DropLimitCheck(
                maxPerProduct = dropProduct.maxPerCustomer,
                maxPerDrop = drop.maxPerCustomer,
                orderedProduct = ordered[dropProduct.product.id] ?: 0,
                orderedDrop = orderedTotal,
                wantedProduct = item.quantity,
                wantedDrop = wantedTotal,
            )
            DropRules.limitProblem(check)?.let { throw DropCartService.limitBroken(it, drop, dropProduct, check) }
            if (item.quantity > dropProduct.available) throw cartChanged(DropCartService.notEnough(dropProduct).message)
        }

        val lines = entry.items.map { item ->
            val dropProduct = item.dropProduct
            val price = dropProduct.priceCents ?: ProductRules.shownPriceCents(dropProduct.product)
                ?: throw cartChanged("${dropProduct.product.name} heeft nog geen prijs.")
            CheckoutLine(dropProduct.product, item.quantity, price)
        }
        val order = checkoutService.newOrder(
            party = party,
            lines = lines,
            viewer = viewer,
            request = request,
            reservationTtl = properties.drops.reservationTtl,
            drop = drop,
            dropEntryId = entry.id,
        )
        for (item in entry.items) dropStockService.reserve(item.dropProduct, item.quantity)
        checkoutService.saveAddressIfAsked(viewer, request, party)
        return order
    }

    private fun cartChanged(message: String) =
        ApiException(HttpStatus.CONFLICT, "cart_changed", "$message Pas je drop-mandje aan en probeer het opnieuw.")
}
