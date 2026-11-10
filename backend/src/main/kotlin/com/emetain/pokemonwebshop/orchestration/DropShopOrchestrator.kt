package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Drop
import com.emetain.pokemonwebshop.domain.DropQueueEntry
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CartLineDto
import com.emetain.pokemonwebshop.dto.CheckoutStartDto
import com.emetain.pokemonwebshop.dto.DropCartDto
import com.emetain.pokemonwebshop.dto.DropCartLimitDto
import com.emetain.pokemonwebshop.dto.StartDropCheckoutRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.emetain.pokemonwebshop.service.CartProblem
import com.emetain.pokemonwebshop.service.CartRules
import com.emetain.pokemonwebshop.service.CheckoutService
import com.emetain.pokemonwebshop.service.DropCartService
import com.emetain.pokemonwebshop.service.DropCheckoutService
import com.emetain.pokemonwebshop.service.DropCookieService
import com.emetain.pokemonwebshop.service.DropQueueService
import com.emetain.pokemonwebshop.service.DropRules
import com.emetain.pokemonwebshop.service.OrderPaymentService
import com.emetain.pokemonwebshop.service.ProductRules
import com.emetain.pokemonwebshop.service.RateLimitService
import com.emetain.pokemonwebshop.service.payment.PaymentGateway
import com.emetain.pokemonwebshop.service.payment.PaymentUnavailableException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** The drop cart and the drop checkout of someone who got through the waiting room. */
@Component
class DropShopOrchestrator(
    private val dropOrchestrator: DropOrchestrator,
    private val dropQueueService: DropQueueService,
    private val dropCartService: DropCartService,
    private val dropCheckoutService: DropCheckoutService,
    private val cartOrchestrator: CartOrchestrator,
    private val checkoutOrchestrator: CheckoutOrchestrator,
    private val checkoutService: CheckoutService,
    private val orderPaymentService: OrderPaymentService,
    private val orderRepository: OrderRepository,
    private val gateway: PaymentGateway,
    private val cookies: DropCookieService,
    private val rateLimitService: RateLimitService,
    private val properties: AppProperties,
    private val clock: Clock,
) {

    @Transactional
    fun cart(slug: String, viewer: User?, httpRequest: HttpServletRequest, country: String?): DropCartDto {
        val (drop, entry) = access(slug, viewer, httpRequest)
        return dto(drop, entry, cartOrchestrator.country(viewer, country))
    }

    @Transactional
    fun setQuantity(slug: String, viewer: User?, httpRequest: HttpServletRequest, dropProductId: Long, quantity: Int, country: String?): DropCartDto {
        viewer?.let { rateLimitService.checkAccount("drop-cart-account", it.id) }
        val (drop, entry) = access(slug, viewer, httpRequest)
        dropCartService.setQuantity(drop, entry, dropProductId, quantity)
        return dto(drop, entry, cartOrchestrator.country(viewer, country))
    }

    /**
     * Holds the drop stock, makes the order and opens Stripe's payment page. An earlier checkout of the same person
     * in this drop that wasn't paid is closed first, so going back and trying again never blocks their own stock.
     */
    fun checkout(slug: String, viewer: User?, httpRequest: HttpServletRequest, request: StartDropCheckoutRequest): CheckoutStartDto {
        checkHoneypot(request, "drop checkout")
        viewer?.let { rateLimitService.checkAccount("drop-checkout-account", it.id) }
        if (!gateway.configured) {
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "payments_unavailable", "Online betalen is nog niet ingesteld. Probeer het later opnieuw.")
        }
        val (drop, entry) = access(slug, viewer, httpRequest)
        for (orderId in orderRepository.findIdsByDropEntryAndStatus(entry.id)) {
            orderPaymentService.closePending(orderId, "Vervangen door een nieuwe poging om af te rekenen")
        }
        val order = retryingOnStockConflict { dropCheckoutService.createOrder(drop.id, entry.id, viewer, request.toCheckout()) }
        val session = try {
            gateway.createCheckout(checkoutOrchestrator.paymentRequest(order, drop.slug))
        } catch (exception: PaymentUnavailableException) {
            checkoutService.releaseReservation(order.id, "De betaalpagina kon niet worden geopend")
            throw ApiException(HttpStatus.BAD_GATEWAY, "payments_unavailable", "We konden de betaalpagina niet openen. Probeer het over een paar minuten opnieuw.")
        }
        checkoutService.attachSession(order.id, session)
        return CheckoutStartDto(order.number, order.accessToken, session.url)
    }

    private fun access(slug: String, viewer: User?, httpRequest: HttpServletRequest): Pair<Drop, DropQueueEntry> {
        val drop = dropOrchestrator.publicDrop(slug)
        val entry = dropQueueService.requireAccess(drop, dropOrchestrator.visitor(viewer, httpRequest), cookies.accessToken(httpRequest))
        return drop to entry
    }

    /** The drop cart in the shape of the shop's cart, so the checkout page shows both the same way. */
    private fun dto(drop: Drop, entry: DropQueueEntry, country: String): DropCartDto {
        val ordered = dropCartService.orderedBy(drop, entry)
        val lines = entry.items.map { item ->
            val dropProduct = item.dropProduct
            val product = dropProduct.product
            val available = dropProduct.available.coerceAtLeast(0)
            val shopPrice = ProductRules.shownPriceCents(product)
            val price = dropProduct.priceCents ?: shopPrice
            val ownImage = product.images.firstOrNull()
            val cardImage = product.card?.shownImageSmallUrl
            val problem = when {
                available <= 0 -> CartProblem.SOLD_OUT
                item.quantity > available -> CartProblem.TOO_MANY
                else -> null
            }
            CartLineDto(
                productId = product.id,
                slug = product.slug,
                name = product.name,
                game = product.game?.slug,
                type = product.type.name,
                imageUrl = ownImage ?: cardImage,
                imageIsCard = ownImage == null && cardImage != null,
                setLogoUrl = product.cardSet?.shownLogoUrl,
                unitPriceCents = price,
                priceCents = shopPrice?.takeIf { price != null && it > price },
                onSale = shopPrice != null && price != null && price < shopPrice,
                quantity = item.quantity,
                lineTotalCents = price?.let { it * item.quantity },
                available = available,
                maxQuantity = CartRules.maxQuantity(available),
                canBuy = available > 0 && price != null,
                preOrder = product.preOrder,
                shipsFrom = if (product.preOrder) product.shipsFrom ?: product.releaseDate else null,
                problem = problem?.name,
            )
        }
        val inCart = entry.items.associate { it.dropProduct.id to it.quantity }
        val orderedTotal = ordered.values.sum()
        val inCartTotal = inCart.values.sum()
        return DropCartDto(
            cart = cartOrchestrator.dto(lines, country),
            limits = drop.products.map { dropProduct ->
                val mine = inCart[dropProduct.id] ?: 0
                val orderedProduct = ordered[dropProduct.product.id] ?: 0
                DropCartLimitDto(
                    dropProductId = dropProduct.id,
                    maxPerCustomer = dropProduct.maxPerCustomer,
                    ordered = orderedProduct,
                    inCart = mine,
                    room = DropRules.roomLeft(dropProduct.maxPerCustomer, drop.maxPerCustomer, orderedProduct, orderedTotal, inCartTotal - mine),
                )
            },
            maxPerCustomer = drop.maxPerCustomer,
            orderedTotal = orderedTotal,
            accessExpiresAt = entry.accessExpiresAt,
            reservationMinutes = properties.drops.reservationTtl.toMinutes(),
            serverTime = clock.instant(),
        )
    }
}
