package com.emetain.pokemonwebshop.security

import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.service.SessionCookieService
import com.emetain.pokemonwebshop.service.SessionService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

const val CURRENT_USER_ATTRIBUTE = "currentUser"

/** Loads the user behind the session cookie and enforces [RequiresLogin] and [AdminOnly]. */
@Component
class AuthInterceptor(
    private val sessionCookieService: SessionCookieService,
    private val sessionService: SessionService,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val user = sessionCookieService.readToken(request)?.let(sessionService::findUser)
        user?.let { request.setAttribute(CURRENT_USER_ATTRIBUTE, it) }

        if (handler is HandlerMethod) {
            enforce(handler, user)
        }
        return true
    }

    private fun enforce(handler: HandlerMethod, user: User?) {
        val adminOnly = handler.isAnnotated(AdminOnly::class.java)
        val requiresLogin = adminOnly || handler.isAnnotated(RequiresLogin::class.java)

        if (requiresLogin && user == null) throw ApiException.notAuthenticated()
        if (adminOnly && user?.role != Role.ADMIN) throw ApiException.forbidden()
    }

    private fun HandlerMethod.isAnnotated(annotation: Class<out Annotation>): Boolean =
        hasMethodAnnotation(annotation) || AnnotatedElementUtils.hasAnnotation(beanType, annotation)
}
