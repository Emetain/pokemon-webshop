package com.emetain.pokemonwebshop.exception

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * The English versions of the backend's own messages (2026-11-21), for visitors who chose English on the website. The
 * code keeps writing its messages in Dutch; `i18n/messages-en.tsv` lists each one with its English text. A message with
 * a value in it, such as "Je kunt maximaal 50 prijsalerts hebben.", is listed with `{}` and matched as a pattern. A
 * message that isn't listed stays Dutch.
 */
@Component
class ErrorMessageTranslator {

    private val exact: Map<String, String>
    private val patterns: List<Pair<Regex, String>>

    init {
        val pairs = ClassPathResource(RESOURCE).inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line -> line.substringBefore('\t') to line.substringAfter('\t') }
                .toList()
        }
        exact = pairs.filter { PLACEHOLDER !in it.first }.toMap()
        patterns = pairs.filter { PLACEHOLDER in it.first }.map { (dutch, english) ->
            Regex(dutch.split(PLACEHOLDER).joinToString("(.+?)") { Regex.escape(it) }) to english
        }
    }

    /** [message] in English, or unchanged when there's no English version. */
    fun english(message: String): String {
        exact[message]?.let { return it }
        for ((pattern, english) in patterns) {
            val match = pattern.matchEntire(message) ?: continue
            val values = match.groupValues.drop(1).iterator()
            return english.split(PLACEHOLDER).reduce { text, next -> text + values.next() + next }
        }
        return message
    }

    private companion object {
        const val RESOURCE = "i18n/messages-en.tsv"
        const val PLACEHOLDER = "{}"
    }
}
