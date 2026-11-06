package com.emetain.pokemonwebshop.security

import io.swagger.v3.oas.annotations.Parameter

/** The endpoint needs a logged-in user: 401 otherwise. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresLogin

/** The endpoint needs an admin: 401 when logged out, 403 for any other logged-in user. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AdminOnly

/**
 * Injects the logged-in user. Use on endpoints guarded by [RequiresLogin] or [AdminOnly]. On a public endpoint,
 * make the parameter nullable to also get visitors who aren't logged in.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(hidden = true)
annotation class CurrentUser

/** Limits calls to this endpoint per IP address. Each [bucket] is counted separately. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimited(val bucket: String)
