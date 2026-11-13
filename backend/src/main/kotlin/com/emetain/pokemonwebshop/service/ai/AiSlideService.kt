package com.emetain.pokemonwebshop.service.ai

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.AiUsage
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.HeroSlide
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.AiUsageRepository
import com.emetain.pokemonwebshop.repository.HeroSlideRepository
import com.emetain.pokemonwebshop.service.ImageFormat
import com.emetain.pokemonwebshop.service.MediaStorage
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What AI calls cost, in millionths of a US dollar. Pure, so the monthly limit can be tested. */
object AiCost {
    private val MILLION = BigDecimal.valueOf(1_000_000)

    /** [inputPerMillion] and [outputPerMillion] are dollars per million tokens. */
    fun micros(inputTokens: Long, outputTokens: Long, inputPerMillion: BigDecimal, outputPerMillion: BigDecimal): Long =
        // Dollars per million tokens is the same number as millionths of a dollar per token.
        (BigDecimal.valueOf(inputTokens) * inputPerMillion + BigDecimal.valueOf(outputTokens) * outputPerMillion)
            .setScale(0, RoundingMode.CEILING)
            .longValueExact()

    fun micros(dollars: BigDecimal): Long = (dollars * MILLION).setScale(0, RoundingMode.CEILING).longValueExact()

    fun dollars(micros: Long): BigDecimal = BigDecimal.valueOf(micros).divide(MILLION, 4, RoundingMode.HALF_UP)

    /** Whether a call that may cost [estimateMicros] still fits in the month. */
    fun fits(spentMicros: Long, estimateMicros: Long, limitMicros: Long): Boolean = spentMicros + estimateMicros <= limitMicros
}

/** The month's spending on AI, against the limit. */
data class AiBudget(val spentMicros: Long, val limitMicros: Long)

/** The AI's draft before it becomes a slide. */
data class SlideDraft(
    val title: String,
    val text: String,
    val buttonLabel: String,
    val linkUrl: String,
    val game: Game?,
    val imagePrompt: String,
)

/**
 * Hero slides written by AI from a prompt such as "pre-order Destined Rivals, release 30 mei". The result is a slide
 * that is switched off, marked as written by AI: nothing goes live before the admin reads, edits and switches it on.
 */
@Service
class AiSlideService(
    private val aiClient: AiClient,
    private val usageRepository: AiUsageRepository,
    private val slideRepository: HeroSlideRepository,
    private val mediaStorage: MediaStorage,
    private val objectMapper: ObjectMapper,
    private val transactions: TransactionTemplate,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    private val settings = properties.openai

    val available: Boolean get() = aiClient.configured

    /** Hero slides only: the chatbot has its own limit. */
    fun budget(): AiBudget = AiBudget(usageRepository.costSince(startOfMonth(), listOf("TEXT", "IMAGE")), AiCost.micros(settings.monthlyLimitUsd))

    /** Calls OpenAI outside any transaction: a slow answer never holds the database. */
    fun draftSlide(prompt: String, withImage: Boolean, admin: User): HeroSlide {
        if (!available) throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai_unavailable", "Er is geen OpenAI-sleutel ingesteld.")
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.length < MIN_PROMPT) throw ApiException.validation("Beschrijf in een paar woorden waar de slide over gaat.")
        val estimate = AiCost.micros(TEXT_ESTIMATE) + if (withImage) AiCost.micros(settings.imageEstimateUsd) else 0
        checkBudget(estimate)

        val text = call { aiClient.json(settings.textModel, INSTRUCTIONS, input(cleanPrompt), "hero_slide", schema()) }
        record("TEXT", settings.textModel, text.inputTokens, text.outputTokens, settings.textInputPerMillion, settings.textOutputPerMillion, admin)
        val draft = parse(text.json)

        val imageUrl = if (withImage) {
            checkBudget(AiCost.micros(settings.imageEstimateUsd))
            val image = call { aiClient.image(settings.imageModel, imagePrompt(draft.imagePrompt), settings.imageSize, settings.imageQuality) }
            val format = ImageFormat.detect(image.bytes) ?: throw ApiException(HttpStatus.BAD_GATEWAY, "ai_failed", "OpenAI gaf een afbeelding die we niet konden lezen.")
            val cost = if (image.inputTokens != null && image.outputTokens != null) {
                AiCost.micros(image.inputTokens, image.outputTokens, settings.imageTextInputPerMillion, settings.imageOutputPerMillion)
            } else {
                AiCost.micros(settings.imageEstimateUsd)
            }
            saveUsage("IMAGE", settings.imageModel, image.inputTokens ?: 0, image.outputTokens ?: 0, cost, admin)
            mediaStorage.store(image.bytes, format)
        } else {
            null
        }

        return transactions.execute {
            slideRepository.save(
                HeroSlide(
                    title = draft.title,
                    position = slideRepository.findHighestPosition() + 1,
                    createdAt = clock.instant(),
                    text = draft.text,
                    buttonLabel = draft.buttonLabel,
                    linkUrl = draft.linkUrl,
                    imageUrl = imageUrl,
                    game = draft.game,
                    active = false,
                    aiGenerated = true,
                ),
            )
        }!!
    }

    private fun checkBudget(estimateMicros: Long) {
        val budget = budget()
        if (!AiCost.fits(budget.spentMicros, estimateMicros, budget.limitMicros)) {
            throw ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "ai_limit",
                "De maandlimiet voor AI is bereikt: \$${AiCost.dollars(budget.spentMicros).setScale(2, RoundingMode.HALF_UP)} " +
                    "van \$${settings.monthlyLimitUsd.setScale(2, RoundingMode.HALF_UP)}. Volgende maand kan het weer. " +
                    "Een hogere limiet zet je met OPENAI_MONTHLY_LIMIT_USD op de server.",
            )
        }
    }

    private fun <T> call(action: () -> T): T = try {
        action()
    } catch (exception: AiException) {
        throw ApiException(HttpStatus.BAD_GATEWAY, "ai_failed", exception.message ?: "OpenAI gaf een fout.")
    }

    private fun record(kind: String, model: String, input: Long, output: Long, inPrice: BigDecimal, outPrice: BigDecimal, admin: User) =
        saveUsage(kind, model, input, output, AiCost.micros(input, output, inPrice, outPrice), admin)

    private fun saveUsage(kind: String, model: String, input: Long, output: Long, cost: Long, admin: User) {
        transactions.executeWithoutResult {
            usageRepository.save(AiUsage(kind, model.take(MODEL_LENGTH), input, output, cost, clock.instant(), admin))
        }
    }

    /** Today's date helps the model with words like "volgende week". */
    private fun input(prompt: String): String =
        "Vandaag is het ${LocalDate.now(clock.withZone(ZONE))}.\n\nOpdracht van de winkel:\n$prompt"

    private fun parse(json: com.fasterxml.jackson.databind.JsonNode): SlideDraft {
        fun field(name: String, max: Int) = json.path(name).asText("").trim().take(max)
        val title = field("title", TITLE_LENGTH)
        if (title.isEmpty()) throw ApiException(HttpStatus.BAD_GATEWAY, "ai_failed", "OpenAI gaf geen titel terug. Probeer het opnieuw.")
        val link = field("link", LINK_LENGTH).takeIf { it in LINKS } ?: "/producten"
        return SlideDraft(
            title = title,
            text = field("text", TEXT_LENGTH),
            buttonLabel = field("buttonLabel", BUTTON_LENGTH).ifEmpty { "Bekijk" },
            linkUrl = link,
            game = Game.fromSlug(field("game", GAME_LENGTH)),
            imagePrompt = field("imagePrompt", IMAGE_PROMPT_LENGTH),
        )
    }

    /** The model's own scene, always with the shop's rules about what may not be in it. */
    private fun imagePrompt(scene: String): String = "$scene\n\n$IMAGE_RULES"

    private fun schema(): ObjectNode {
        val schema = objectMapper.createObjectNode()
        schema.put("type", "object")
        schema.put("additionalProperties", false)
        val props = schema.putObject("properties")
        props.putObject("title").put("type", "string").put("description", "Kop van de slide, Nederlands, hoogstens 60 tekens.")
        props.putObject("text").put("type", "string").put("description", "Eén of twee zinnen, Nederlands, hoogstens 160 tekens.")
        props.putObject("buttonLabel").put("type", "string").put("description", "Knoptekst, Nederlands, hoogstens 25 tekens.")
        props.putObject("link").apply {
            put("type", "string")
            put("description", "De pagina waar de knop heen gaat.")
            putArray("enum").apply { LINKS.forEach(::add) }
        }
        props.putObject("game").apply {
            put("type", "string")
            put("description", "Het spel waar de slide over gaat, of none.")
            putArray("enum").apply { (Game.entries.map { it.slug } + "none").forEach(::add) }
        }
        props.putObject("imagePrompt").put("type", "string").put(
            "description",
            "An English description of a background picture for the slide: a scene with objects, light and colour. " +
                "No characters, creatures, logos, brand names, card art or text.",
        )
        schema.putArray("required").apply { listOf("title", "text", "buttonLabel", "link", "game", "imagePrompt").forEach(::add) }
        return schema
    }

    private fun startOfMonth(): Instant = LocalDate.now(clock.withZone(ZONE)).withDayOfMonth(1).atStartOfDay(ZONE).toInstant()

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")

        /** A slide costs a few hundred tokens: two cents is far above that. */
        val TEXT_ESTIMATE = BigDecimal("0.02")

        const val MIN_PROMPT = 5
        const val TITLE_LENGTH = 120
        const val TEXT_LENGTH = 300
        const val BUTTON_LENGTH = 40
        const val LINK_LENGTH = 40
        const val GAME_LENGTH = 20
        const val IMAGE_PROMPT_LENGTH = 1500
        const val MODEL_LENGTH = 60

        /** Where a slide may link to. The model picks one, the admin can change it. */
        val LINKS = listOf(
            "/pre-order", "/releasekalender", "/drops", "/nieuws", "/producten", "/pokemon", "/lorcana", "/magic",
            "/one-piece", "/riftbound", "/accessoires", "/collectie",
        )

        val INSTRUCTIONS = """
            Je schrijft een slide voor de grote carrousel bovenaan de homepage van Poketopia, een Nederlandse webshop
            voor trading card games: Pokémon, Disney Lorcana, Magic: The Gathering, One Piece Card Game en Riftbound.
            De winkel geeft een korte opdracht, jij schrijft de kop, de tekst en de knoptekst.

            - Schrijf in het Nederlands, met je en jij, kort, enthousiast en duidelijk.
            - Gebruik alleen feiten uit de opdracht. Verzin geen prijzen, kortingen, datums, aantallen of beloftes.
            - Schrijf namen van sets en producten zoals in de opdracht.
            - Kies de pagina waar de knop heen moet uit de lijst, en het spel als de opdracht er één noemt.
            - Beschrijf voor de achtergrondafbeelding een sfeervol tafereel zonder personages, dieren of wezens,
              zonder logo's of merknamen, zonder kaartafbeeldingen en zonder tekst: die zijn van de makers van de spellen.
        """.trimIndent()

        /** Pokémon and other characters, logos and card art belong to the games' owners. */
        val IMAGE_RULES = """
            Rules for this picture: do not show any Pokémon or other characters, creatures, mascots or people. Do not
            show logos, trademarks, brand names, trading card artwork or any readable text. Only objects, scenery,
            light and abstract shapes. Wide banner composition with calm space on the left for text.
        """.trimIndent().replace("\n", " ")
    }
}
