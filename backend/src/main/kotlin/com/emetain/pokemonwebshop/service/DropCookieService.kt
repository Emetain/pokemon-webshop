package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import org.springframework.web.util.WebUtils
import java.time.Duration

/**
 * The cookies of drops. `drop_visitor` tells guests apart in the waiting room of a drop without account. `drop_access`
 * holds the access token of one drop, on that drop's path only, so being in two drops at once keeps both. The database
 * only has the hashes.
 */
@Service
class DropCookieService(
    private val properties: AppProperties,
) {
    fun visitorToken(request: HttpServletRequest): String? = read(request, properties.drops.visitorCookieName)

    fun accessToken(request: HttpServletRequest): String? = read(request, properties.drops.accessCookieName)

    fun visitorCookie(token: String, request: HttpServletRequest): ResponseCookie =
        cookie(properties.drops.visitorCookieName, token, "${request.contextPath}/v1/drops", VISITOR_TTL)

    /** Lasts as long as the access itself. */
    fun accessCookie(token: String, slug: String, validFor: Duration, request: HttpServletRequest): ResponseCookie =
        cookie(properties.drops.accessCookieName, token, "${request.contextPath}/v1/drops/$slug", validFor.coerceAtLeast(Duration.ZERO))

    private fun cookie(name: String, value: String, path: String, maxAge: Duration): ResponseCookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(properties.auth.cookieSecure)
            .sameSite("Lax")
            .path(path)
            .maxAge(maxAge)
            .build()

    private fun read(request: HttpServletRequest, name: String): String? =
        WebUtils.getCookie(request, name)?.value?.takeIf { it.length in TOKEN_LENGTHS }

    private companion object {
        val VISITOR_TTL: Duration = Duration.ofDays(30)

        /** The tokens of [TokenService] are 43 characters. Anything else isn't ours. */
        val TOKEN_LENGTHS = 20..100
    }
}
