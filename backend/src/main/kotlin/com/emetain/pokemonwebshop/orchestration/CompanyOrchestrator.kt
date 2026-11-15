package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.dto.CompanyDto
import org.springframework.stereotype.Component

/** The business details from the server's settings (`app.company`), for the footer of every page. */
@Component
class CompanyOrchestrator(
    private val properties: AppProperties,
) {

    fun company(): CompanyDto = with(properties.company) {
        CompanyDto(
            legalName = legalName.filled(),
            address = address.filled(),
            kvkNumber = kvkNumber.filled(),
            vatNumber = vatNumber.filled(),
        )
    }

    private fun String.filled(): String? = trim().takeIf { it.isNotEmpty() }
}
