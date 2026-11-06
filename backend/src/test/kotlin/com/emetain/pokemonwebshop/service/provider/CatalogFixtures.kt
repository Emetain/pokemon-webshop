package com.emetain.pokemonwebshop.service.provider

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

private val objectMapper = ObjectMapper()

fun json(text: String): JsonNode = objectMapper.readTree(text)

/** A real API answer, trimmed to the fields we read. */
fun fixture(name: String): JsonNode {
    val stream = requireNotNull(object {}.javaClass.getResourceAsStream("/catalog/$name")) { "No fixture $name" }
    return stream.use(objectMapper::readTree)
}
