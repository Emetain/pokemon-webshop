package com.emetain.pokemonwebshop.exception

import org.springframework.http.HttpStatus

/** An expected failure. [code] is stable for the frontend, [message] is shown to the customer in Dutch. */
class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message) {

    companion object {
        fun notAuthenticated() =
            ApiException(HttpStatus.UNAUTHORIZED, "not_authenticated", "Log in om verder te gaan.")

        fun forbidden() =
            ApiException(HttpStatus.FORBIDDEN, "forbidden", "Je hebt geen toegang tot deze pagina.")

        fun notFound(message: String = "We konden dit niet vinden.") =
            ApiException(HttpStatus.NOT_FOUND, "not_found", message)

        fun validation(message: String) =
            ApiException(HttpStatus.BAD_REQUEST, "validation_failed", message)

        fun outOfStock(available: Int) = ApiException(
            HttpStatus.CONFLICT,
            "out_of_stock",
            if (available <= 0) "Dit product is uitverkocht." else "Er zijn er nog maar $available van dit product.",
        )

        fun stockChanged() = ApiException(
            HttpStatus.CONFLICT,
            "stock_changed",
            "De voorraad is net door iemand anders gewijzigd. Probeer het opnieuw.",
        )

        fun syncRunning() =
            ApiException(HttpStatus.CONFLICT, "sync_running", "Er loopt al een synchronisatie voor dit spel.")

        fun rateLimited(retryAfterSeconds: Long) = ApiException(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate_limited",
            "Te veel pogingen. Probeer het over een minuut opnieuw.",
            retryAfterSeconds,
        )
    }
}
