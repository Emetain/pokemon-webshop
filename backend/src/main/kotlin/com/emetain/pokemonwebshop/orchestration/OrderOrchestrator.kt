package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.GuestAccountRequest
import com.emetain.pokemonwebshop.dto.OrderDto
import com.emetain.pokemonwebshop.dto.OrderPageDto
import com.emetain.pokemonwebshop.dto.UserDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.emetain.pokemonwebshop.service.CartCookieService
import com.emetain.pokemonwebshop.service.CartService
import com.emetain.pokemonwebshop.service.OrderNumbers
import com.emetain.pokemonwebshop.service.OrderPaymentService
import com.emetain.pokemonwebshop.service.OrderService
import com.emetain.pokemonwebshop.service.SessionCookieService
import com.emetain.pokemonwebshop.service.SessionService
import com.emetain.pokemonwebshop.service.UserService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.time.Clock

/** Orders as customers see them: in the account, or as a guest through the link with the order's key. */
@Component
class OrderOrchestrator(
    private val orderRepository: OrderRepository,
    private val orderPaymentService: OrderPaymentService,
    private val userService: UserService,
    private val sessionService: SessionService,
    private val sessionCookieService: SessionCookieService,
    private val cartService: CartService,
    private val cartCookieService: CartCookieService,
    private val orderService: OrderService,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {

    fun view(number: String, token: String?, viewer: User?): OrderDto = transactions.execute {
        val order = accessible(number, token, viewer)
        OrderDto.from(order, canCreateAccount(order))
    }!!

    /** From the page Stripe sends a customer to after "terug": the stock goes back right away. */
    fun cancelPending(number: String, token: String?, viewer: User?): OrderDto {
        val order = accessible(number, token, viewer)
        if (order.status == OrderStatus.PENDING_PAYMENT) {
            orderPaymentService.closePending(order.id, "Betaling afgebroken door de klant")
        }
        return view(number, token, viewer)
    }

    @Transactional(readOnly = true)
    fun mine(user: User, page: Int): OrderPageDto =
        OrderPageDto.from(orderRepository.findPaidByUser(user.id, PageRequest.of(page.coerceAtLeast(0), PAGE_SIZE)))

    @Transactional(readOnly = true)
    fun myOrder(user: User, number: String): OrderDto {
        val order = orderRepository.findByNumber(OrderNumbers.normalize(number))
            ?.takeIf { it.user?.id == user.id && it.paidAt != null }
            ?: throw notFound()
        return OrderDto.from(order)
    }

    /** A guest who just paid makes an account with the email of the order, and is logged in right away. */
    fun createAccount(number: String, request: GuestAccountRequest, httpRequest: HttpServletRequest): ResponseEntity<UserDto> {
        val order = accessible(number, request.token, null)
        if (!canCreateAccount(order)) {
            throw ApiException(HttpStatus.CONFLICT, "account_not_possible", "Voor deze bestelling kun je geen account meer aanmaken. Log in of registreer je via de accountpagina.")
        }
        val user = userService.register(order.email, request.password, order.customerName)
        orderService.linkToUser(order.id, user)
        val response = ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, sessionCookieService.create(sessionService.create(user)).toString())
        cartCookieService.readToken(httpRequest)?.let { token ->
            if (cartService.mergeGuestCart(token, user)) response.header(HttpHeaders.SET_COOKIE, cartCookieService.clear().toString())
        }
        return response.body(UserDto.from(user))
    }

    private fun canCreateAccount(order: Order): Boolean {
        val paidAt = order.paidAt ?: return false
        return order.user == null &&
            clock.instant().isBefore(paidAt.plus(OrderService.GUEST_ACCOUNT_WINDOW)) &&
            userService.findByEmail(order.email) == null
    }

    /**
     * The owner, an admin, or whoever has the key. Anyone else gets the same answer as for an unknown number. The
     * order comes with its lines and user, so it can be read outside a transaction.
     */
    private fun accessible(number: String, token: String?, viewer: User?): Order {
        val order = orderRepository.findByNumber(OrderNumbers.normalize(number)) ?: throw notFound()
        val owner = viewer != null && order.user?.id == viewer.id
        val admin = viewer?.role == Role.ADMIN
        val keyMatches = token != null &&
            MessageDigest.isEqual(token.toByteArray(), order.accessToken.toByteArray())
        if (!owner && !admin && !keyMatches) throw notFound()
        return order
    }

    private fun notFound() = ApiException.notFound("We konden deze bestelling niet vinden.")

    private companion object {
        const val PAGE_SIZE = 10
    }
}
