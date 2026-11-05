package com.emetain.pokemonwebshop.config

import com.emetain.pokemonwebshop.security.AuthInterceptor
import com.emetain.pokemonwebshop.security.CurrentUserArgumentResolver
import com.emetain.pokemonwebshop.security.RateLimitInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val rateLimitInterceptor: RateLimitInterceptor,
    private val authInterceptor: AuthInterceptor,
    private val currentUserArgumentResolver: CurrentUserArgumentResolver,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        // Rate limiting goes first so that blocked requests never reach the database.
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/v1/**")
        registry.addInterceptor(authInterceptor).addPathPatterns("/v1/**")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentUserArgumentResolver)
    }
}
