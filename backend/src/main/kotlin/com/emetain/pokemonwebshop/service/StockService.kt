package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.StockMovement
import com.emetain.pokemonwebshop.domain.StockReason
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.repository.StockMovementRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** Published after stock or a product changed in a way that can make it buyable. */
data class ProductAvailabilityChanged(val productId: Long)

/**
 * The only way stock changes. Every change writes a [StockMovement]. Two changes that start from the same stock
 * can't both be saved: the version on the stock row makes the second one fail, so the last item is sold once.
 */
@Service
class StockService(
    private val productRepository: ProductRepository,
    private val movementRepository: StockMovementRepository,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) {

    /** For the admin: stock bought in, a return, a correction. [change] is the change to the quantity on hand. */
    @Transactional
    fun adjust(productId: Long, change: Int, reason: StockReason, note: String?, admin: User?): StockMovement {
        if (change == 0) throw ApiException.validation("Vul een aantal in dat niet 0 is.")
        when (reason) {
            StockReason.PURCHASE, StockReason.RETURN ->
                if (change < 0) throw ApiException.validation("Inkoop en retouren tellen op. Gebruik een correctie om af te boeken.")
            StockReason.SALE, StockReason.DROP_ALLOCATION ->
                if (change > 0) throw ApiException.validation("Een verkoop of drop-toewijzing boekt af.")
            StockReason.CORRECTION -> Unit
            StockReason.RESERVATION, StockReason.RELEASE ->
                throw ApiException.validation("Reserveringen lopen via de winkelwagen, niet via een aanpassing.")
        }
        val product = find(productId)
        val stock = product.stock
        if (stock.onHand + change < stock.reserved) {
            throw ApiException.validation(
                "Er zijn ${stock.reserved} gereserveerd, dus er moeten er minstens zoveel op voorraad blijven.",
            )
        }
        return record(product, change, reason, note, admin) { stock.onHand += change }
    }

    /** Sets the quantity on hand to [target] with a correction, as a count or a CSV import does. Null when nothing changes. */
    @Transactional
    fun correctTo(productId: Long, target: Int, note: String?, admin: User?): StockMovement? {
        if (target < 0) throw ApiException.validation("Voorraad kan niet negatief zijn.")
        val current = find(productId).stock.onHand
        return if (target == current) null else adjust(productId, target - current, StockReason.CORRECTION, note, admin)
    }

    /** Holds [quantity] for a checkout. Fails when fewer are left. */
    @Transactional
    fun reserve(productId: Long, quantity: Int, note: String? = null): StockMovement {
        requirePositive(quantity)
        val product = find(productId)
        val stock = product.stock
        if (stock.available < quantity) throw ApiException.outOfStock(stock.available)
        return record(product, quantity, StockReason.RESERVATION, note, null) { stock.reserved += quantity }
    }

    /** Gives back a reservation that wasn't paid. Never more than is reserved. */
    @Transactional
    fun release(productId: Long, quantity: Int, note: String? = null): StockMovement? {
        requirePositive(quantity)
        val product = find(productId)
        val stock = product.stock
        val released = minOf(quantity, stock.reserved)
        if (released == 0) return null
        return record(product, -released, StockReason.RELEASE, note, null) { stock.reserved -= released }
    }

    /**
     * Moves [quantity] from the normal stock into a drop. Only what nobody holds can go: a checkout keeps its
     * reservation. Fails when fewer are left.
     */
    @Transactional
    fun allocateToDrop(productId: Long, quantity: Int, note: String, admin: User?): StockMovement {
        requirePositive(quantity)
        val product = find(productId)
        val stock = product.stock
        if (stock.available < quantity) {
            throw ApiException.validation(
                if (stock.available <= 0) "Er is geen vrije voorraad van ${product.name} om toe te wijzen."
                else "Er zijn nog maar ${stock.available} van ${product.name} vrij om toe te wijzen.",
            )
        }
        return record(product, -quantity, StockReason.DROP_ALLOCATION, note, admin) { stock.onHand -= quantity }
    }

    /** Gives stock of a drop back to the normal stock: unsold at the end, or allocated too much. */
    @Transactional
    fun returnFromDrop(productId: Long, quantity: Int, note: String, admin: User?): StockMovement {
        requirePositive(quantity)
        val product = find(productId)
        return record(product, quantity, StockReason.DROP_ALLOCATION, note, admin) { product.stock.onHand += quantity }
    }

    /** Sells [quantity] that wasn't reserved. Fails when fewer are left, so the last item can only be sold once. */
    @Transactional
    fun sell(productId: Long, quantity: Int, note: String? = null): StockMovement {
        requirePositive(quantity)
        val product = find(productId)
        val stock = product.stock
        if (stock.available < quantity) throw ApiException.outOfStock(stock.available)
        return record(product, -quantity, StockReason.SALE, note, null) { stock.onHand -= quantity }
    }

    /** Sells what a checkout reserved, once it is paid. The reservation becomes the sale, in one movement. */
    @Transactional
    fun sellReserved(productId: Long, quantity: Int, note: String? = null): StockMovement {
        requirePositive(quantity)
        val product = find(productId)
        val stock = product.stock
        check(stock.reserved >= quantity && stock.onHand >= quantity) {
            "Product $productId has ${stock.reserved} reserved and ${stock.onHand} on hand, can't sell $quantity reserved"
        }
        return record(product, -quantity, StockReason.SALE, note, null) {
            stock.reserved -= quantity
            stock.onHand -= quantity
        }
    }

    private fun record(
        product: Product,
        change: Int,
        reason: StockReason,
        note: String?,
        admin: User?,
        apply: () -> Unit,
    ): StockMovement {
        val stock = product.stock
        val wasAvailable = stock.available > 0
        apply()
        val now = clock.instant()
        if (!wasAvailable && stock.available > 0) {
            product.arrivedAt = now
            events.publishEvent(ProductAvailabilityChanged(product.id))
        }
        val movement = StockMovement(
            product = product,
            change = change,
            reason = reason,
            onHandAfter = stock.onHand,
            reservedAfter = stock.reserved,
            createdAt = now,
            note = note?.trim()?.takeIf { it.isNotEmpty() }?.take(NOTE_LENGTH),
            adminUser = admin,
        )
        // Flushing here makes a lost race fail inside this call, where the caller can still react to it.
        productRepository.flush()
        return movementRepository.save(movement)
    }

    private fun find(productId: Long): Product =
        productRepository.findById(productId).orElseThrow { ApiException.notFound("We konden dit product niet vinden.") }

    private fun requirePositive(quantity: Int) {
        if (quantity <= 0) throw ApiException.validation("Kies minstens 1 stuk.")
    }

    private companion object {
        const val NOTE_LENGTH = 500
    }
}
