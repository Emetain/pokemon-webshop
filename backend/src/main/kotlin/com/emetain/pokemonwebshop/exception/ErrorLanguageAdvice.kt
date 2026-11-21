package com.emetain.pokemonwebshop.exception

import com.emetain.pokemonwebshop.dto.ErrorResponse
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

/**
 * Puts every error answer in the visitor's language (2026-11-21): the website sends `Accept-Language: en` when the
 * visitor chose English. The admin area stays Dutch, like its pages.
 */
@RestControllerAdvice
class ErrorLanguageAdvice(private val translator: ErrorMessageTranslator) : ResponseBodyAdvice<Any> {

    override fun supports(returnType: MethodParameter, converterType: Class<out HttpMessageConverter<*>>) = true

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        if (body !is ErrorResponse || !wantsEnglish(request)) return body
        return body.copy(message = translator.english(body.message))
    }

    private fun wantsEnglish(request: ServerHttpRequest): Boolean {
        if (ADMIN_PATH in request.uri.path) return false
        val language = request.headers.getFirst(HttpHeaders.ACCEPT_LANGUAGE) ?: return false
        return language.trim().lowercase().startsWith("en")
    }

    private companion object {
        const val ADMIN_PATH = "/v1/admin/"
    }
}
