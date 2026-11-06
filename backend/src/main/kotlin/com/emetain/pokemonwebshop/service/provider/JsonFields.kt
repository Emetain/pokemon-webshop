package com.emetain.pokemonwebshop.service.provider

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** The text of a field, or null when it is missing, null or blank. */
internal fun JsonNode.text(field: String): String? =
    path(field).takeIf { it.isValueNode && !it.isNull }?.asText()?.takeIf { it.isNotBlank() }

internal fun JsonNode.requireText(field: String): String =
    requireNotNull(text(field)) { "Field '$field' is missing in $this" }

internal fun JsonNode.date(field: String): LocalDate? =
    text(field)?.let {
        try {
            LocalDate.parse(it)
        } catch (exception: DateTimeParseException) {
            null
        }
    }
