package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Drop
import com.emetain.pokemonwebshop.domain.DropProduct
import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * The stock of drops. Allocating moves stock out of the normal stock with a `DROP_ALLOCATION` movement, giving back
 * moves it in again. Everything else, holding for a checkout and selling, happens inside the drop. Every method runs
 * under the lock on the drop.
 */
@Service
class DropStockService(
    private val dropLocks: DropLocks,
    private val stockService: StockService,
    private val clock: Clock,
) {

    /**
     * Changes how much of a product the drop has. More is taken from the free normal stock, less goes back to it.
     * Never below what is sold or held. Call this with the drop locked.
     */
    fun setAllocation(drop: Drop, dropProduct: DropProduct, target: Int, admin: User?) {
        if (target < 0) throw ApiException.validation("Een toewijzing kan niet negatief zijn.")
        val taken = dropProduct.sold + dropProduct.reserved
        if (target < taken) {
            throw ApiException.validation("Er zijn er al $taken verkocht of gereserveerd, dus er moeten er minstens zoveel in de drop blijven.")
        }
        val change = target - dropProduct.allocated
        val productId = dropProduct.product.id
        when {
            change > 0 -> stockService.allocateToDrop(productId, change, "Drop ${drop.name}", admin)
            change < 0 -> stockService.returnFromDrop(productId, -change, "Terug uit drop ${drop.name}", admin)
        }
        dropProduct.allocated = target
        if (dropProduct.available > 0) dropProduct.soldOutAt = null
    }

    /** Holds the stock of a drop checkout. Call this with the drop locked, after checking that enough is left. */
    fun reserve(dropProduct: DropProduct, quantity: Int) {
        check(dropProduct.available >= quantity) { "Drop product ${dropProduct.id} has ${dropProduct.available} left, can't hold $quantity" }
        dropProduct.reserved += quantity
    }

    /** A drop order is paid: what it held is sold. */
    @Transactional
    fun sellReserved(order: Order) {
        val drop = locked(order)
        val now = clock.instant()
        for (line in order.lines) {
            val dropProduct = productOf(drop, line.product.id)
            check(dropProduct.reserved >= line.quantity) {
                "Drop product ${dropProduct.id} holds ${dropProduct.reserved}, can't sell ${line.quantity} for order ${order.number}"
            }
            dropProduct.reserved -= line.quantity
            dropProduct.sold += line.quantity
            if (dropProduct.sold >= dropProduct.allocated && dropProduct.soldOutAt == null) dropProduct.soldOutAt = now
        }
    }

    /**
     * A drop checkout wasn't paid: what it held goes back to the drop, for the next person in line. After the drop
     * gave its leftover back, it goes straight to the normal stock instead.
     */
    @Transactional
    fun release(order: Order) {
        val drop = locked(order)
        for (line in order.lines) {
            val dropProduct = productOf(drop, line.product.id)
            val released = minOf(line.quantity, dropProduct.reserved)
            if (released == 0) continue
            dropProduct.reserved -= released
            if (drop.returnsReleasedStock) {
                dropProduct.allocated -= released
                stockService.returnFromDrop(line.product.id, released, "Terug uit drop ${drop.name}: bestelling ${order.number} niet betaald", null)
            }
        }
    }

    /**
     * Gives everything that isn't sold or held back to the normal stock. Stock that a checkout still holds follows
     * when it is released. Call this with the drop locked. Returns how many went back.
     */
    fun returnLeftover(drop: Drop, admin: User?): Int {
        var returned = 0
        for (dropProduct in drop.products) {
            val left = dropProduct.available
            if (left <= 0) continue
            stockService.returnFromDrop(dropProduct.product.id, left, "Niet verkocht in drop ${drop.name}", admin)
            dropProduct.allocated -= left
            returned += left
        }
        drop.leftoverReturnedAt = clock.instant()
        return returned
    }

    private fun locked(order: Order): Drop {
        val dropId = checkNotNull(order.drop) { "Order ${order.number} isn't a drop order" }.id
        return checkNotNull(dropLocks.lock(dropId)) { "Drop $dropId is gone" }
    }

    private fun productOf(drop: Drop, productId: Long): DropProduct =
        checkNotNull(drop.products.firstOrNull { it.product.id == productId }) { "Product $productId isn't in drop ${drop.id}" }
}
