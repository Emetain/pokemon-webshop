package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import org.springframework.web.util.WebUtils
import java.time.Duration

/** The cookie that holds a guest's cart. The database only has the hash of its value. */
@Service
class CartCookieService(
    private val properties: AppProperties,
) {
    fun readToken(request: HttpServletRequest): String? =
        WebUtils.getCookie(request, properties.checkout.cartCookieName)?.value?.takeIf { it.length in TOKEN_LENGTHS }

    fun create(token: String): ResponseCookie = cookie(token, properties.checkout.guestCartTtl)

    fun clear(): ResponseCookie = cookie("", Duration.ZERO)

    private fun cookie(value: String, maxAge: Duration): ResponseCookie =
        ResponseCookie.from(properties.checkout.cartCookieName, value)
            .httpOnly(true)
            .secure(properties.auth.cookieSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build()

    private companion object {
        /** The tokens of [TokenService] are 43 characters. Anything else isn't ours. */
        val TOKEN_LENGTHS = 20..100
    }
}
