package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Drop
import com.emetain.pokemonwebshop.domain.DropCartItem
import com.emetain.pokemonwebshop.domain.DropProduct
import com.emetain.pokemonwebshop.domain.DropQueueEntry
import com.emetain.pokemonwebshop.domain.DropStatus
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.DropQueueEntryRepository
import com.emetain.pokemonwebshop.repository.OrderRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * The drop cart of someone who got through the waiting room. It holds no stock, like the shop's cart. The limits are
 * checked here, when something goes in, and again under the lock on the drop when the checkout starts.
 */
@Service
class DropCartService(
    private val entryRepository: DropQueueEntryRepository,
    private val orderRepository: OrderRepository,
    private val entityManager: EntityManager,
    private val clock: Clock,
) {

    /**
     * Per product, what someone has in the drop's orders that count: by account, email, address or waiting room
     * entry. A null value isn't used to match.
     */
    fun ordered(drop: Drop, user: User?, emailKey: String?, addressKey: String?, entryId: Long?): Map<Long, Int> =
        orderRepository.dropQuantities(drop.id, user?.id, emailKey, addressKey, entryId)
            .associate { it.productId to it.quantity.toInt() }

    /** Before checkout the address isn't known yet: the account, its email and the entry count. */
    fun orderedBy(drop: Drop, entry: DropQueueEntry): Map<Long, Int> =
        ordered(drop, entry.user, entry.user?.email?.let(DropRules::emailKey), null, entry.id)

    /**
     * Sets how many of a product are in the drop cart. Lowering always works. Raising only up to what is left and what
     * the limits allow. 0 takes it out. Call this inside a transaction with the entry loaded.
     */
    @Transactional
    fun setQuantity(drop: Drop, entry: DropQueueEntry, dropProductId: Long, quantity: Int) {
        if (drop.status != DropStatus.LIVE) throw DropQueueService.ended()
        if (quantity !in 0..CartRules.MAX_PER_LINE) throw ApiException.validation("Kies een aantal van 0 tot ${CartRules.MAX_PER_LINE}.")
        val dropProduct = drop.products.firstOrNull { it.id == dropProductId }
            ?: throw ApiException.notFound("Dit product zit niet in deze drop.")
        // One change at a time per person, so two clicks at once can't both pass the limits.
        entityManager.lock(entry, LockModeType.PESSIMISTIC_WRITE)
        val item = entry.items.firstOrNull { it.dropProduct.id == dropProductId }
        val current = item?.quantity ?: 0
        if (quantity == 0) {
            item?.let { entry.items.remove(it) }
            return
        }
        if (quantity > current) {
            if (quantity > dropProduct.available) throw notEnough(dropProduct)
            val ordered = orderedBy(drop, entry)
            val inCartOther = entry.items.filter { it !== item }.sumOf { it.quantity }
            val check = DropLimitCheck(
                maxPerProduct = dropProduct.maxPerCustomer,
                maxPerDrop = drop.maxPerCustomer,
                orderedProduct = ordered[dropProduct.product.id] ?: 0,
                orderedDrop = ordered.values.sum(),
                wantedProduct = quantity,
                wantedDrop = inCartOther + quantity,
            )
            DropRules.limitProblem(check)?.let { throw limitBroken(it, drop, dropProduct, check) }
        }
        if (item == null) {
            entry.items += DropCartItem(entry, dropProduct, quantity, clock.instant())
        } else {
            item.quantity = quantity
        }
    }

    /** After a payment: what was bought leaves the drop cart. */
    @Transactional
    fun removeOrdered(entryId: Long, quantities: Map<Long, Int>) {
        val entry = entryRepository.findById(entryId).orElse(null) ?: return
        val iterator = entry.items.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            val bought = quantities[item.dropProduct.product.id] ?: continue
            item.quantity -= bought
            if (item.quantity <= 0) iterator.remove()
        }
    }

    companion object {
        fun notEnough(dropProduct: DropProduct) = ApiException(
            HttpStatus.CONFLICT,
            "out_of_stock",
            when {
                dropProduct.available > 0 -> "Er zijn er nog maar ${dropProduct.available} van ${dropProduct.product.name}."
                dropProduct.reserved > 0 -> "${dropProduct.product.name} is op dit moment helemaal gereserveerd. Er kan nog iets vrijkomen."
                else -> "${dropProduct.product.name} is uitverkocht."
            },
        )

        fun limitBroken(problem: DropLimitProblem, drop: Drop, dropProduct: DropProduct, check: DropLimitCheck): ApiException {
            val message = when (problem) {
                DropLimitProblem.PRODUCT -> buildString {
                    append("Je kunt van ${dropProduct.product.name} maximaal ${dropProduct.maxPerCustomer} kopen in deze drop.")
                    if (check.orderedProduct > 0) append(" Je hebt er al ${check.orderedProduct} besteld.")
                }
                DropLimitProblem.DROP -> buildString {
                    append("Je kunt maximaal ${drop.maxPerCustomer} producten kopen in deze drop.")
                    if (check.orderedDrop > 0) append(" Je hebt er al ${check.orderedDrop} besteld.")
                }
            }
            return ApiException(HttpStatus.CONFLICT, "drop_limit", message)
        }
    }
}
