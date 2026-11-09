package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.ForgotPasswordRequest
import com.emetain.pokemonwebshop.dto.LoginRequest
import com.emetain.pokemonwebshop.dto.RegisterRequest
import com.emetain.pokemonwebshop.dto.ResetPasswordRequest
import com.emetain.pokemonwebshop.dto.UserDto
import com.emetain.pokemonwebshop.service.CartCookieService
import com.emetain.pokemonwebshop.service.CartService
import com.emetain.pokemonwebshop.service.MailService
import com.emetain.pokemonwebshop.service.PasswordResetService
import com.emetain.pokemonwebshop.service.SessionCookieService
import com.emetain.pokemonwebshop.service.SessionService
import com.emetain.pokemonwebshop.service.UserService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Component
class AuthOrchestrator(
    private val userService: UserService,
    private val sessionService: SessionService,
    private val sessionCookieService: SessionCookieService,
    private val passwordResetService: PasswordResetService,
    private val mailService: MailService,
    private val cartService: CartService,
    private val cartCookieService: CartCookieService,
) {

    fun register(request: RegisterRequest, httpRequest: HttpServletRequest): ResponseEntity<UserDto> {
        val user = userService.register(request.email, request.password, request.displayName)
        return startSession(user, HttpStatus.CREATED, httpRequest)
    }

    fun login(request: LoginRequest, httpRequest: HttpServletRequest): ResponseEntity<UserDto> {
        val user = userService.authenticate(request.email, request.password)
        return startSession(user, HttpStatus.OK, httpRequest)
    }

    fun logout(request: HttpServletRequest): ResponseEntity<Void> {
        sessionCookieService.readToken(request)?.let(sessionService::delete)
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, sessionCookieService.clear().toString())
            .build()
    }

    fun me(user: User): UserDto = UserDto.from(user)

    /** Gives the same result for known and unknown addresses, so nobody can test who has an account. */
    fun forgotPassword(request: ForgotPasswordRequest) {
        passwordResetService.issue(request.email)?.let { issued ->
            mailService.sendPasswordReset(issued.user.email, issued.user.displayName, issued.token)
        }
    }

    fun resetPassword(request: ResetPasswordRequest) {
        passwordResetService.reset(request.token, request.password)
    }

    /** The guest cart goes into the account, and its cookie goes away. */
    private fun startSession(user: User, status: HttpStatus, httpRequest: HttpServletRequest): ResponseEntity<UserDto> {
        val token = sessionService.create(user)
        val response = ResponseEntity.status(status)
            .header(HttpHeaders.SET_COOKIE, sessionCookieService.create(token).toString())
        cartCookieService.readToken(httpRequest)?.let { cartToken ->
            if (cartService.mergeGuestCart(cartToken, user)) {
                response.header(HttpHeaders.SET_COOKIE, cartCookieService.clear().toString())
            }
        }
        return response.body(UserDto.from(user))
    }
}
