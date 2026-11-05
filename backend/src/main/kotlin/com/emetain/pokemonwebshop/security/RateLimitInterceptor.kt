package com.emetain.pokemonwebshop.security

import com.emetain.pokemonwebshop.service.RateLimitService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/** Enforces [RateLimited] per client IP. */
@Component
class RateLimitInterceptor(
    private val rateLimitService: RateLimitService,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val limit = (handler as? HandlerMethod)?.getMethodAnnotation(RateLimited::class.java)
        limit?.let { rateLimitService.check(it.bucket, request.remoteAddr) }
        return true
    }
}
