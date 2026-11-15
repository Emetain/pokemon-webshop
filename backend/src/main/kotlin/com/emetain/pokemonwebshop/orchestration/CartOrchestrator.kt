package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Cart
import com.emetain.pokemonwebshop.domain.DEFAULT_COUNTRY
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.domain.SUPPORTED_COUNTRIES
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CartDiscountDto
import com.emetain.pokemonwebshop.dto.CartDto
import com.emetain.pokemonwebshop.dto.CartItemRequest
import com.emetain.pokemonwebshop.dto.CartLineDto
import com.emetain.pokemonwebshop.dto.CartShippingDto
import com.emetain.pokemonwebshop.service.CartCookieService
import com.emetain.pokemonwebshop.service.CartOwner
import com.emetain.pokemonwebshop.service.CartRules
import com.emetain.pokemonwebshop.service.CartService
import com.emetain.pokemonwebshop.service.DiscountCodeService
import com.emetain.pokemonwebshop.service.DiscountLine
import com.emetain.pokemonwebshop.service.DiscountQuote
import com.emetain.pokemonwebshop.service.OrderMath
import com.emetain.pokemonwebshop.service.PaymentMethods
import com.emetain.pokemonwebshop.service.PricedLine
import com.emetain.pokemonwebshop.service.ProductRules
import com.emetain.pokemonwebshop.service.ShippingPrice
import com.emetain.pokemonwebshop.service.ShippingRateService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CartOrchestrator(
    private val cartService: CartService,
    private val cartCookieService: CartCookieService,
    private val shippingRateService: ShippingRateService,
    private val discountCodeService: DiscountCodeService,
) {

    /** With [discountCode], the totals show what that code takes off, or why it doesn't apply. */
    @Transactional(readOnly = true)
    fun get(viewer: User?, request: HttpServletRequest, country: String?, discountCode: String? = null): CartDto {
        val cart = cartService.find(owner(viewer, request))
        return dto(lines(cart), country(viewer, country), discountCode, viewer, drop = false)
    }

    @Transactional
    fun add(viewer: User?, request: HttpServletRequest, body: CartItemRequest, country: String?): ResponseEntity<CartDto> {
        val change = cartService.add(owner(viewer, request), body.productId, body.quantity)
        val response = ResponseEntity.ok()
        change.newGuestToken?.let { response.header(HttpHeaders.SET_COOKIE, cartCookieService.create(it).toString()) }
        return response.body(dto(change.cart, country(viewer, country)))
    }

    @Transactional
    fun setQuantity(viewer: User?, request: HttpServletRequest, productId: Long, quantity: Int, country: String?): CartDto =
        dto(cartService.setQuantity(owner(viewer, request), productId, quantity), country(viewer, country))

    @Transactional
    fun remove(viewer: User?, request: HttpServletRequest, productId: Long, country: String?): CartDto =
        dto(cartService.remove(owner(viewer, request), productId), country(viewer, country))

    fun owner(viewer: User?, request: HttpServletRequest) =
        CartOwner(viewer, if (viewer == null) cartCookieService.readToken(request) else null)

    /** Call this inside a transaction. The totals use today's prices: an order copies them at checkout. */
    fun dto(cart: Cart?, country: String): CartDto = dto(lines(cart), country)

    private fun lines(cart: Cart?): List<CartLineDto> {
        val items = cart?.items.orEmpty()
        return items.map { item ->
            val product = item.product
            val canBuy = ProductRules.canBuy(product)
            val unitPrice = ProductRules.shownPriceCents(product)
            val ownImage = product.images.firstOrNull()
            val cardImage = product.card?.shownImageSmallUrl
            CartLineDto(
                productId = product.id,
                slug = product.slug,
                name = product.name,
                game = product.game?.slug,
                type = product.type.name,
                imageUrl = ownImage ?: cardImage,
                imageIsCard = ownImage == null && cardImage != null,
                setLogoUrl = product.cardSet?.shownLogoUrl,
                unitPriceCents = unitPrice,
                priceCents = product.priceCents,
                onSale = ProductRules.onSale(product.priceCents, product.salePriceCents),
                quantity = item.quantity,
                lineTotalCents = unitPrice?.let { it * item.quantity },
                maxQuantity = if (canBuy) CartRules.MAX_PER_LINE else 0,
                canBuy = canBuy,
                preOrder = product.preOrder,
                shipsFrom = if (product.preOrder) product.shipsFrom ?: product.releaseDate else null,
                problem = CartRules.problem(item.quantity, canBuy)?.name,
            )
        }
    }

    /**
     * Totals, shipping and payment methods for lines that are priced already, from the shop cart or a drop cart.
     * With [discountCode], the totals include what the code takes off. Checkout checks the code again.
     */
    fun dto(
        lines: List<CartLineDto>,
        country: String,
        discountCode: String? = null,
        viewer: User? = null,
        drop: Boolean = false,
    ): CartDto {
        val payable = lines.filter { it.problem == null && it.unitPriceCents != null }
        val priced = payable.map { PricedLine(it.unitPriceCents!!, it.quantity) }
        val quote = discountCode?.takeIf { it.isNotBlank() && payable.isNotEmpty() }
            ?.let { discountCodeService.quote(it, payable.map(::discountLine), viewer, drop) }
        val rate = shippingRateService.active(country)
        val totals = OrderMath.totals(priced, rate?.let { ShippingPrice(it.priceCents, it.freeFromCents) }, quote?.amountCents ?: 0)
        return CartDto(
            items = lines,
            itemCount = lines.sumOf { it.quantity },
            subtotalCents = totals.subtotalCents,
            discountCents = totals.discountCents,
            discount = quote?.let(::discountDto),
            shipping = rate?.let {
                CartShippingDto(
                    country = it.country,
                    priceCents = totals.shippingCents,
                    ratePriceCents = it.priceCents,
                    freeFromCents = it.freeFromCents,
                    remainingForFreeCents = totals.remainingForFreeShippingCents,
                    deliveryTime = it.deliveryTime,
                )
            },
            totalCents = totals.totalCents,
            vatCents = totals.vatCents,
            canCheckout = lines.isNotEmpty() && lines.all { it.problem == null } && rate != null,
            hasPreOrders = lines.any { it.preOrder },
            hasInStock = lines.any { !it.preOrder },
            paymentMethods = PaymentMethods.forCountry(country),
        )
    }

    private fun discountLine(line: CartLineDto) = DiscountLine(
        unitPriceCents = line.unitPriceCents!!,
        quantity = line.quantity,
        game = line.game?.let(Game::fromSlug),
        type = ProductType.valueOf(line.type),
    )

    private fun discountDto(quote: DiscountQuote) =
        CartDiscountDto(quote.code, quote.amountCents, applied = quote.problem == null, message = quote.problem)

    /** The country from the region setting, else the customer's, else the Netherlands. */
    fun country(viewer: User?, requested: String?): String =
        requested?.uppercase()?.takeIf { it in SUPPORTED_COUNTRIES } ?: viewer?.country ?: DEFAULT_COUNTRY
}
