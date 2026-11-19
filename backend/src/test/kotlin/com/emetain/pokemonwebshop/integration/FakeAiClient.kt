package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.service.ai.AiClient
import com.emetain.pokemonwebshop.service.ai.AiImageResult
import com.emetain.pokemonwebshop.service.ai.AiTextResult
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

/** Stands in for OpenAI, so tests never call it and never cost money. Keeps the prompts it got. */
class FakeAiClient : AiClient {
    private val objectMapper = ObjectMapper()

    val textInputs = CopyOnWriteArrayList<String>()
    val instructions = CopyOnWriteArrayList<String>()
    val models = CopyOnWriteArrayList<String>()

    /** What the chatbot answers in a test. */
    @Volatile var chatAnswer = """{"answer": "Bestellingen gaan binnen 1 werkdag op de post.", "suggestTicket": false}"""
    val imagePrompts = CopyOnWriteArrayList<String>()

    @Volatile var configuredInTest = true

    /** What the scanner reads from a photo in a test (phase 11). */
    @Volatile var scanReading = """{"game": "pokemon", "name": "Pikachu", "setCode": null, "setName": null, "number": "25"}"""
    val scannedPhotos = CopyOnWriteArrayList<ByteArray>()

    @Volatile var failScan = false

    override fun visionJson(
        model: String,
        instructions: String,
        image: ByteArray,
        contentType: String,
        schemaName: String,
        schema: ObjectNode,
    ): AiTextResult {
        if (failScan) throw com.emetain.pokemonwebshop.service.ai.AiException("The model is down in this test")
        scannedPhotos += image
        models += model
        return AiTextResult(objectMapper.readTree(scanReading), inputTokens = 1000, outputTokens = 60)
    }

    override val configured: Boolean get() = configuredInTest

    override fun json(model: String, instructions: String, input: String, schemaName: String, schema: ObjectNode): AiTextResult {
        textInputs += input
        this.instructions += instructions
        models += model
        if (schemaName == "support_answer") return AiTextResult(objectMapper.readTree(chatAnswer), inputTokens = 4000, outputTokens = 150)
        val json = objectMapper.readTree(
            """
            {"title": "Destined Rivals in pre-order", "text": "Bestel nu, dan heb je hem op 30 mei binnen.",
             "buttonLabel": "Bestel nu", "link": "/pre-order", "game": "pokemon",
             "imagePrompt": "A table with sealed boxes in warm evening light"}
            """.trimIndent(),
        )
        return AiTextResult(json, inputTokens = 600, outputTokens = 120)
    }

    /** A real WebP header, so the shop can store it. */
    override fun image(model: String, prompt: String, size: String, quality: String): AiImageResult {
        imagePrompts += prompt
        val webp = "RIFF".toByteArray() + byteArrayOf(0x10, 0, 0, 0) + "WEBPVP8 ".toByteArray() + ByteArray(8)
        return AiImageResult(webp, inputTokens = 80, outputTokens = 1568)
    }
}

@TestConfiguration
class TestAiConfig {
    @Bean
    @Primary
    fun fakeAiClient() = FakeAiClient()
}
