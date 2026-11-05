package com.emetain.pokemonwebshop.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(TcgdexProperties::class)
class TcgdexConfig {

    @Bean
    fun tcgdexRestClient(builder: RestClient.Builder, properties: TcgdexProperties): RestClient =
        builder.baseUrl(properties.baseUrl).build()
}
