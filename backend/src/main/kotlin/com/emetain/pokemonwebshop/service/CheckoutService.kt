package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderLine
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.OrderStatusChange
import com.emetain.pokemonwebshop.domain.OrderStockState
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.SUPPORTED_COUNTRIES
import com.emetain.pokemonwebshop.domain.ShipmentPreference
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AddressRequest
import com.emetain.pokemonwebshop.dto.StartCheckoutRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.AddressRepository
import com.emetain.pokemonwebshop.repository.CartRepository
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.emetain.pokemonwebshop.service.payment.CheckoutSession
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Locale

/** Turns a cart into an order that holds its stock, and lets go of that stock when the order isn't paid. */
@Service
class CheckoutService(
    private val orderRepository: OrderRepository,
    private val cartRepository: CartRepository,
    private val addressRepository: AddressRepository,
    private val addressService: AddressService,
    private val shippingRateService: ShippingRateService,
    private val stockService: StockService,
    private val tokenService: TokenService,
    private val properties: AppProperties,
    private val clock: Clock,
) {

    /**
     * Copies the cart into an order with the current prices and reserves the stock of every line. Everything happens
     * in one transaction: when one product has too few left, nothing is reserved and nothing is saved.
     */
    @Transactional
    fun createOrder(cartId: Long, viewer: User?, request: StartCheckoutRequest): Order {
        if (!request.acceptTerms) throw ApiException.validation("Ga akkoord met de algemene voorwaarden om te bestellen.")
        val cart = cartRepository.findWithItemsById(cartId)
        if (cart == null || cart.items.isEmpty()) throw ApiException.validation("Je winkelwagen is leeg.")
        if (cart.items.size > CartRules.MAX_LINES) {
            throw ApiException.validation("Je kunt maximaal ${CartRules.MAX_LINES} verschillende producten in één bestelling doen.")
        }
        val (name, email) = contact(viewer, request)
        val address = address(viewer, request)
        val rate = shippingRateService.active(address.country)
            ?: throw ApiException.validation("We verzenden op dit moment niet naar dit land.")

        for (item in cart.items) {
            val product = item.product
            val available = product.stock.available
            if (product.status != ProductStatus.ACTIVE || !ProductRules.canBuy(product)) throw cartChanged("${product.name} is uitverkocht.")
            if (item.quantity > available) throw cartChanged("Van ${product.name} zijn er nog maar $available.")
        }
        val priced = cart.items.map { PricedLine(ProductRules.shownPriceCents(it.product)!!, it.quantity) }
        val totals = OrderMath.totals(priced, ShippingPrice(rate.priceCents, rate.freeFromCents))
        val mixed = cart.items.any { it.product.preOrder } && cart.items.any { !it.product.preOrder }
        val now = clock.instant()
        val order = Order(
            number = uniqueNumber(),
            user = viewer,
            email = email,
            customerName = name,
            street = address.street.trim(),
            houseNumber = address.houseNumber.trim(),
            postcode = addressService.normalizePostcode(address.postcode, address.country),
            city = address.city.trim(),
            country = address.country,
            subtotalCents = totals.subtotalCents,
            shippingCents = totals.shippingCents,
            totalCents = totals.totalCents,
            vatCents = totals.vatCents,
            deliveryTime = rate.deliveryTime,
            shipmentPreference = if (mixed) request.shipmentPreference ?: ShipmentPreference.TOGETHER else ShipmentPreference.TOGETHER,
            accessToken = tokenService.generate(),
            cartId = cart.id,
            termsAcceptedAt = now,
            createdAt = now,
            updatedAt = now,
            reservedUntil = now.plus(properties.checkout.reservationTtl),
        )
        cart.items.zip(priced).forEach { (item, price) ->
            val product = item.product
            val ownImage = product.images.firstOrNull()
            val cardImage = product.card?.shownImageSmallUrl
            order.lines += OrderLine(
                order = order,
                product = product,
                productName = product.name,
                productSlug = product.slug,
                productType = product.type,
                imageUrl = ownImage ?: cardImage,
                imageIsCard = ownImage == null && cardImage != null,
                unitPriceCents = price.unitPriceCents,
                quantity = price.quantity,
                lineTotalCents = price.totalCents,
                preOrder = product.preOrder,
                shipsFrom = if (product.preOrder) product.shipsFrom ?: product.releaseDate else null,
            )
        }
        order.history += OrderStatusChange(order, OrderStatus.PENDING_PAYMENT, now, "Afgerekend, wacht op de betaling")
        orderRepository.save(order)
        for (line in order.lines) stockService.reserve(line.product.id, line.quantity, "Bestelling ${order.number}")

        if (viewer != null && request.saveAddress && request.addressId == null) {
            addressService.create(viewer, address)
        }
        return order
    }

    @Transactional
    fun attachSession(orderId: Long, session: CheckoutSession) {
        val order = orderRepository.findLockedById(orderId) ?: return
        order.stripeSessionId = session.id
        order.stripeSessionExpiresAt = session.expiresAt
        order.updatedAt = clock.instant()
    }

    /**
     * Gives the reserved stock back and cancels the order. Only for an order that wasn't paid: the caller first makes
     * sure nobody can still pay for it. Returns whether anything changed.
     */
    @Transactional
    fun releaseReservation(orderId: Long, reason: String, admin: User? = null): Boolean {
        val order = orderRepository.findLockedById(orderId) ?: return false
        if (order.status != OrderStatus.PENDING_PAYMENT || order.stockState != OrderStockState.RESERVED) return false
        for (line in order.lines) stockService.release(line.product.id, line.quantity, "Bestelling ${order.number} niet betaald")
        val now = clock.instant()
        order.stockState = OrderStockState.RELEASED
        order.reservedUntil = null
        order.status = OrderStatus.CANCELLED
        order.cancelledAt = now
        order.updatedAt = now
        order.history += OrderStatusChange(order, OrderStatus.CANCELLED, now, reason, admin)
        return true
    }

    /** A logged-in customer orders in the name of the profile. A guest fills in a name and email. */
    private fun contact(viewer: User?, request: StartCheckoutRequest): Pair<String, String> {
        if (viewer != null) return viewer.displayName to viewer.email
        val name = request.name?.trim().orEmpty()
        if (name.isEmpty()) throw ApiException.validation("Vul je naam in.")
        val email = request.email?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (!EMAIL.matches(email)) throw ApiException.validation("Vul een geldig e-mailadres in.")
        return name to email
    }

    private fun address(viewer: User?, request: StartCheckoutRequest): AddressRequest {
        val address = if (request.addressId != null) {
            if (viewer == null) throw ApiException.validation("Log in om een opgeslagen adres te gebruiken.")
            val saved = addressRepository.findByIdAndUserId(request.addressId, viewer.id)
                ?: throw ApiException.notFound("We konden dit adres niet vinden.")
            AddressRequest(saved.street, saved.houseNumber, saved.postcode, saved.city, saved.country)
        } else {
            request.address ?: throw ApiException.validation("Vul je adres in.")
        }
        if (address.country !in SUPPORTED_COUNTRIES) throw ApiException.validation("We verzenden alleen naar Nederland en België.")
        return address
    }

    private fun uniqueNumber(): String =
        generateSequence { OrderNumbers.generate() }.first { !orderRepository.existsByNumber(it) }

    private fun cartChanged(message: String) =
        ApiException(HttpStatus.CONFLICT, "cart_changed", "$message Pas je winkelwagen aan en probeer het opnieuw.")

    private companion object {
        val EMAIL = Regex("[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}")
    }
}
