package com.emetain.pokemonwebshop.exception

import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The English versions of the backend's messages (2026-11-21). */
class ErrorMessageTranslatorTest {

    private val translator = ErrorMessageTranslator()

    @Test
    fun `a listed message comes back in English`() {
        assertEquals("Email address or password isn't right.", translator.english("E-mailadres of wachtwoord klopt niet."))
    }

    @Test
    fun `a message with a value keeps the value`() {
        assertEquals("You can create at most 20 binders.", translator.english("Je kunt maximaal 20 binders maken."))
        assertEquals(
            "You already have 2 orders waiting for payment. Pay for or cancel one first, or try again in 15 minutes.",
            translator.english(
                "Je hebt al 2 bestellingen die op betaling wachten. Betaal of annuleer er eerst een, of probeer het over 15 minuten opnieuw.",
            ),
        )
        assertEquals("Too many attempts. Try again in 3 minutes.", translator.english("Te veel pogingen. Probeer het over 3 minuten opnieuw."))
    }

    @Test
    fun `a message that isn't listed stays as it is`() {
        assertEquals("Iets heel nieuws.", translator.english("Iets heel nieuws."))
    }

    @Test
    fun `every line has a Dutch and an English text with the same values`() {
        val lines = ClassPathResource("i18n/messages-en.tsv").inputStream.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(lines.size > 300)
        for (line in lines) {
            val parts = line.split('\t')
            assertEquals(2, parts.size, line)
            assertTrue(parts.all { it.isNotBlank() }, line)
            assertEquals(parts[0].split("{}").size, parts[1].split("{}").size, line)
        }
    }
}
