package com.emetain.pokemonwebshop.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "tcgdex")
data class TcgdexProperties(
    val baseUrl: String,
)
