package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.config.AppProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class CatalogHttpException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Calls the card APIs politely: it identifies itself, pauses before every request and honours rate limits. */
@Component
class CatalogHttpClient(
    private val properties: AppProperties,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val client = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun getJson(url: String, pause: Duration): JsonNode = get(url, pause) { objectMapper.readTree(it) }

    /** Streams the response into [read]. A retry calls [read] again, so it must start from nothing each time. */
    fun <T> get(url: String, pause: Duration, read: (InputStream) -> T): T {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", properties.catalog.userAgent)
            .header("Accept", "application/json, */*;q=0.8")
            .GET()
            .build()

        for (attempt in 1..MAX_ATTEMPTS) {
            Thread.sleep(pause)
            when (val outcome = attempt(request, attempt, read)) {
                is Outcome.Success -> return outcome.value
                is Outcome.Retry -> {
                    if (attempt == MAX_ATTEMPTS) {
                        throw CatalogHttpException("$url failed after $MAX_ATTEMPTS attempts: ${outcome.reason}")
                    }
                    log.warn("{} failed ({}), trying again in {}", url, outcome.reason, outcome.wait)
                    Thread.sleep(outcome.wait)
                }
            }
        }
        error("The loop always returns or throws")
    }

    private fun <T> attempt(request: HttpRequest, attempt: Int, read: (InputStream) -> T): Outcome<T> =
        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { body ->
                val status = response.statusCode()
                when {
                    status in 200..299 -> Outcome.Success(read(body))
                    status == 429 -> Outcome.Retry(retryAfter(response), "HTTP 429")
                    status >= 500 -> Outcome.Retry(retryDelay(attempt), "HTTP $status")
                    else -> throw CatalogHttpException("${request.uri()} answered HTTP $status")
                }
            }
        } catch (exception: IOException) {
            Outcome.Retry(retryDelay(attempt), exception.toString())
        }

    private fun retryDelay(attempt: Int): Duration = properties.catalog.retryDelay.multipliedBy(attempt.toLong())

    private fun retryAfter(response: HttpResponse<*>): Duration =
        response.headers().firstValue("Retry-After").orElse(null)
            ?.toLongOrNull()
            ?.let(Duration::ofSeconds)
            ?: properties.catalog.rateLimitDelay

    private sealed interface Outcome<out T> {
        data class Success<T>(val value: T) : Outcome<T>
        data class Retry(val wait: Duration, val reason: String) : Outcome<Nothing>
    }

    private companion object {
        const val MAX_ATTEMPTS = 4
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(20)
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}
