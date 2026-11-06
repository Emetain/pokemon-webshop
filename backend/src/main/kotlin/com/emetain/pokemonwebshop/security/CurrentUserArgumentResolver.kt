package com.emetain.pokemonwebshop.security

import com.emetain.pokemonwebshop.exception.ApiException
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUser::class.java)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? = webRequest.getAttribute(CURRENT_USER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
        ?: if (parameter.isOptional) null else throw ApiException.notAuthenticated()
}
