package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import org.springframework.web.util.WebUtils
import java.time.Duration

@Service
class SessionCookieService(
    private val properties: AppProperties,
) {

    fun readToken(request: HttpServletRequest): String? =
        WebUtils.getCookie(request, properties.auth.cookieName)?.value?.takeIf { it.isNotBlank() }

    fun create(token: String): ResponseCookie = cookie(token, properties.auth.sessionTtl)

    fun clear(): ResponseCookie = cookie("", Duration.ZERO)

    private fun cookie(value: String, maxAge: Duration): ResponseCookie =
        ResponseCookie.from(properties.auth.cookieName, value)
            .httpOnly(true)
            .secure(properties.auth.cookieSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build()
}
