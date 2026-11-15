package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Cart
import com.emetain.pokemonwebshop.domain.CartItem
import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CartRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/** Whose cart: the logged-in customer's, or the guest's behind the cookie. A logged-in customer never uses a guest cart. */
data class CartOwner(val user: User?, val guestToken: String?)

/** A changed cart, and the token for a new guest cookie when this change created the cart. */
data class CartChange(val cart: Cart?, val newGuestToken: String? = null)

@Service
class CartService(
    private val cartRepository: CartRepository,
    private val productRepository: ProductRepository,
    private val tokenService: TokenService,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun find(owner: CartOwner): Cart? = when {
        owner.user != null -> cartRepository.findByUserId(owner.user.id)
        owner.guestToken != null -> cartRepository.findByGuestTokenHash(tokenService.hash(owner.guestToken))
        else -> null
    }

    /**
     * Adds [quantity] to what is in the cart already, up to [CartRules.MAX_PER_LINE]. Only for products that can be
     * bought right now. The stock isn't looked at: a refusal at "just above the stock" would tell how many are left.
     * Checkout checks the stock.
     */
    @Transactional
    fun add(owner: CartOwner, productId: Long, quantity: Int): CartChange {
        requireQuantity(quantity)
        val product = productRepository.findWithDetailsById(productId)?.takeIf { it.status == ProductStatus.ACTIVE }
            ?: throw ApiException.notFound("We konden dit product niet vinden.")
        if (!ProductRules.canBuy(product)) throw notBuyable(product)
        val (cart, newToken) = findOrCreate(owner)
        val item = cart.items.firstOrNull { it.product.id == productId }
        val inCart = item?.quantity ?: 0
        if (inCart + quantity > CartRules.MAX_PER_LINE) throw tooMany(inCart)
        if (item == null) {
            cart.items.add(CartItem(cart, product, quantity, clock.instant()))
        } else {
            item.quantity = inCart + quantity
        }
        cart.updatedAt = clock.instant()
        return CartChange(cartRepository.save(cart), newToken)
    }

    /**
     * Lowering always works, also for a product that sold out. Raising only for a product that can be bought, up to
     * [CartRules.MAX_PER_LINE], without looking at the stock. 0 removes it.
     */
    @Transactional
    fun setQuantity(owner: CartOwner, productId: Long, quantity: Int): Cart {
        if (quantity < 0 || quantity > CartRules.MAX_PER_LINE) throw ApiException.validation("Kies een aantal van 0 tot ${CartRules.MAX_PER_LINE}.")
        val cart = find(owner) ?: throw ApiException.notFound("Je winkelwagen is leeg.")
        val item = cart.items.firstOrNull { it.product.id == productId }
            ?: throw ApiException.notFound("Dit product zit niet in je winkelwagen.")
        if (quantity == 0) {
            cart.items.remove(item)
        } else if (quantity > item.quantity) {
            val product = item.product
            if (!ProductRules.canBuy(product)) throw notBuyable(product)
            item.quantity = quantity
        } else {
            item.quantity = quantity
        }
        cart.updatedAt = clock.instant()
        return cart
    }

    @Transactional
    fun remove(owner: CartOwner, productId: Long): Cart? {
        val cart = find(owner) ?: return null
        if (cart.items.removeIf { it.product.id == productId }) cart.updatedAt = clock.instant()
        return cart
    }

    /**
     * On login: the guest cart goes into the account cart. Quantities of a product in both add up, up to
     * [CartRules.MAX_PER_LINE]. Returns whether there was a guest cart, so the caller can clear its cookie.
     */
    @Transactional
    fun mergeGuestCart(guestToken: String, user: User): Boolean {
        val guest = cartRepository.findByGuestTokenHash(tokenService.hash(guestToken)) ?: return false
        if (guest.items.isNotEmpty()) {
            val (cart, _) = findOrCreate(CartOwner(user, null))
            val now = clock.instant()
            for (guestItem in guest.items) {
                val existing = cart.items.firstOrNull { it.product.id == guestItem.product.id }
                val merged = CartRules.mergedQuantity(existing?.quantity ?: 0, guestItem.quantity)
                if (existing == null) {
                    cart.items.add(CartItem(cart, guestItem.product, merged, now))
                } else {
                    existing.quantity = merged
                }
            }
            cart.updatedAt = now
            cartRepository.save(cart)
        }
        cartRepository.delete(guest)
        return true
    }

    /** After a payment: what was bought leaves the cart. Anything the customer added later stays. */
    @Transactional
    fun removeOrdered(cartId: Long, quantities: Map<Long, Int>) {
        val cart = cartRepository.findWithItemsById(cartId) ?: return
        val iterator = cart.items.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            val bought = quantities[item.product.id] ?: continue
            item.quantity -= bought
            if (item.quantity <= 0) iterator.remove()
        }
        cart.updatedAt = clock.instant()
    }

    @Transactional
    fun deleteGuestCartsOlderThan(age: Duration): Int {
        val before = clock.instant().minus(age)
        cartRepository.deleteGuestItemsBefore(before)
        return cartRepository.deleteGuestCartsBefore(before)
    }

    private fun findOrCreate(owner: CartOwner): Pair<Cart, String?> {
        find(owner)?.let { return it to null }
        val now = clock.instant()
        if (owner.user != null) return cartRepository.save(Cart(user = owner.user, createdAt = now, updatedAt = now)) to null
        val token = tokenService.generate()
        val cart = cartRepository.save(Cart(guestTokenHash = tokenService.hash(token), createdAt = now, updatedAt = now))
        return cart to token
    }

    private fun requireQuantity(quantity: Int) {
        if (quantity !in 1..CartRules.MAX_PER_LINE) throw ApiException.validation("Kies een aantal van 1 tot ${CartRules.MAX_PER_LINE}.")
    }

    private fun notBuyable(product: Product) = if (product.stock.available <= 0) {
        ApiException.outOfStock(0)
    } else {
        ApiException(HttpStatus.CONFLICT, "not_for_sale", "Dit product kun je nu niet bestellen.")
    }

    /** About the fixed maximum per line, never about the stock: the shop doesn't show how many are left. */
    private fun tooMany(inCart: Int) = ApiException(
        HttpStatus.CONFLICT,
        "too_many",
        buildString {
            append("Je kunt maximaal ${CartRules.MAX_PER_LINE} stuks van dit product in één bestelling doen.")
            if (inCart > 0) append(" Je hebt er al $inCart in je winkelwagen.")
        },
    )
}

/** Quantities in a cart. Pure, so it can be tested without a database. */
object CartRules {
    /**
     * The most of one product in one order. Low on purpose: the cart doesn't look at the stock, so a customer only
     * finds out at checkout that a quantity isn't there, and a low maximum keeps what such a try says small. The owner
     * chose 10 in phase 9.
     */
    const val MAX_PER_LINE = 10

    /** At most 50 different products: Stripe takes up to 100 lines, and this keeps the payment page readable. */
    const val MAX_LINES = 50

    /**
     * Both carts had the product: the quantities add up, up to [MAX_PER_LINE]. Never fewer than the account had, so a
     * line that already shows a problem keeps showing it.
     */
    fun mergedQuantity(inAccount: Int, inGuestCart: Int): Int =
        (inAccount + inGuestCart).coerceAtMost(maxOf(MAX_PER_LINE, inAccount))

    /**
     * What is wrong with a line before checkout, or null. Only what the customer may know: that a product can't be
     * bought, or that a line is above the maximum (from a cart made before the maximum was lowered). Never that a
     * line is above the stock: checkout tells that, without a number.
     */
    fun problem(quantity: Int, canBuy: Boolean): CartProblem? = when {
        !canBuy -> CartProblem.SOLD_OUT
        quantity > MAX_PER_LINE -> CartProblem.TOO_MANY
        else -> null
    }
}

/** `TOO_MANY`: above [CartRules.MAX_PER_LINE], never "above the stock". */
enum class CartProblem { SOLD_OUT, TOO_MANY }
