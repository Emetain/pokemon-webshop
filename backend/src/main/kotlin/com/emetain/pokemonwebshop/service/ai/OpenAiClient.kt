package com.emetain.pokemonwebshop.service.ai

import com.emetain.pokemonwebshop.config.AppProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/** JSON that a text model wrote to a schema, with the tokens it used. */
data class AiTextResult(val json: JsonNode, val inputTokens: Long, val outputTokens: Long)

/** A generated image, with the tokens it used. Null tokens when the provider didn't report them. */
data class AiImageResult(val bytes: ByteArray, val inputTokens: Long?, val outputTokens: Long?)

/** Why a call to the AI failed, in words for the admin. */
class AiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Text and images from an AI provider. Behind an interface, so tests run without OpenAI. */
interface AiClient {
    val configured: Boolean

    /** Asks [model] for JSON that matches [schema]. */
    fun json(model: String, instructions: String, input: String, schemaName: String, schema: ObjectNode): AiTextResult

    fun image(model: String, prompt: String, size: String, quality: String): AiImageResult
}

/**
 * OpenAI's API, called from the backend only: the key never reaches the browser. Text through the Responses API with
 * Structured Outputs, images through the Image API. Nothing is stored at OpenAI (`store: false`). Checked against
 * OpenAI's API docs on 2026-11-12.
 */
@Component
class OpenAiClient(
    properties: AppProperties,
    private val objectMapper: ObjectMapper,
) : AiClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val settings = properties.openai
    private val http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()

    override val configured: Boolean get() = settings.configured

    override fun json(model: String, instructions: String, input: String, schemaName: String, schema: ObjectNode): AiTextResult {
        val body = objectMapper.createObjectNode().apply {
            put("model", model)
            put("instructions", instructions)
            put("input", input)
            put("store", false)
            putObject("text").putObject("format").apply {
                put("type", "json_schema")
                put("name", schemaName)
                put("strict", true)
                set<JsonNode>("schema", schema)
            }
        }
        val response = post("/responses", body)
        if (response.path("status").asText() == "incomplete") {
            throw AiException("OpenAI stopte halverwege (${response.path("incomplete_details").path("reason").asText("onbekend")}). Probeer het opnieuw.")
        }
        val content = response.path("output")
            .filter { it.path("type").asText() == "message" }
            .flatMap { it.path("content").toList() }
        content.firstOrNull { it.path("type").asText() == "refusal" }?.let {
            throw AiException("OpenAI wilde deze tekst niet schrijven: ${it.path("refusal").asText()}")
        }
        val text = content.firstOrNull { it.path("type").asText() == "output_text" }?.path("text")?.asText()
            ?: throw AiException("OpenAI gaf geen tekst terug. Probeer het opnieuw.")
        val json = try {
            objectMapper.readTree(text)
        } catch (exception: Exception) {
            throw AiException("OpenAI gaf tekst terug die we niet konden lezen. Probeer het opnieuw.", exception)
        }
        val usage = response.path("usage")
        return AiTextResult(json, usage.path("input_tokens").asLong(0), usage.path("output_tokens").asLong(0))
    }

    override fun image(model: String, prompt: String, size: String, quality: String): AiImageResult {
        val body = objectMapper.createObjectNode().apply {
            put("model", model)
            put("prompt", prompt)
            put("size", size)
            put("quality", quality)
            put("output_format", "webp")
            put("n", 1)
        }
        val response = post("/images/generations", body)
        val encoded = response.path("data").firstOrNull()?.path("b64_json")?.asText()?.takeIf { it.isNotEmpty() }
            ?: throw AiException("OpenAI gaf geen afbeelding terug. Probeer het opnieuw.")
        val usage = response.path("usage")
        return AiImageResult(
            bytes = Base64.getDecoder().decode(encoded),
            inputTokens = usage.path("input_tokens").takeIf { it.isNumber }?.asLong(),
            outputTokens = usage.path("output_tokens").takeIf { it.isNumber }?.asLong(),
        )
    }

    private fun post(path: String, body: JsonNode): JsonNode {
        if (!configured) throw AiException("Er is geen OpenAI-sleutel ingesteld.")
        val request = HttpRequest.newBuilder(URI.create(settings.baseUrl.trimEnd('/') + path))
            .timeout(settings.timeout)
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build()
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (exception: java.io.IOException) {
            throw AiException("We konden OpenAI niet bereiken. Probeer het over een paar minuten opnieuw.", exception)
        }
        val json = runCatching { objectMapper.readTree(response.body()) }.getOrNull()
        if (response.statusCode() in SUCCESS) return json ?: throw AiException("OpenAI gaf een antwoord dat we niet konden lezen.")
        val error = json?.path("error")
        log.warn("OpenAI {} answered {}: {} ({})", path, response.statusCode(), error?.path("message")?.asText(), error?.path("code")?.asText())
        throw AiException(
            when {
                error?.path("code")?.asText() == "moderation_blocked" ->
                    "OpenAI maakte dit niet, vanwege zijn inhoudsregels. Probeer een andere omschrijving."
                response.statusCode() == UNAUTHORIZED -> "De OpenAI-sleutel klopt niet of is ingetrokken."
                response.statusCode() == TOO_MANY -> "OpenAI is even te druk, of het tegoed bij OpenAI is op. Probeer het later opnieuw."
                response.statusCode() == BAD_REQUEST -> "OpenAI kon deze vraag niet aan: ${error?.path("message")?.asText().orEmpty()}".trim()
                else -> "OpenAI gaf een fout (${response.statusCode()}). Probeer het later opnieuw."
            },
        )
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        val SUCCESS = 200..299
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val TOO_MANY = 429
    }
}
