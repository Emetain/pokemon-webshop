package com.emetain.pokemonwebshop.integration

import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.HttpMethod
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/** One endpoint of the application, as its request mappings describe it. */
data class Endpoint(val method: HttpMethod, val pattern: String, val handler: HandlerMethod) {
    /** Any value will do for a path variable: the login check happens before the handler reads it. */
    val path: String = pattern.replace(Regex("\\{[^}]+}"), "1")

    fun isAnnotated(annotation: Class<out Annotation>): Boolean =
        handler.hasMethodAnnotation(annotation) || AnnotatedElementUtils.hasAnnotation(handler.beanType, annotation)

    override fun toString() = "$method $pattern"
}

/** Every endpoint the application has, so a new one is covered by the tests without changing them. */
fun RequestMappingHandlerMapping.endpoints(): List<Endpoint> =
    handlerMethods.flatMap { (info, handler) ->
        val methods = info.methodsCondition.methods.map { HttpMethod.valueOf(it.name) }.ifEmpty { listOf(HttpMethod.GET) }
        info.patternValues.flatMap { pattern -> methods.map { Endpoint(it, pattern, handler) } }
    }.sortedBy { it.toString() }
