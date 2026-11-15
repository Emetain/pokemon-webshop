package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.dto.CompanyDto
import com.emetain.pokemonwebshop.service.testProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CompanyOrchestratorTest {

    @Test
    fun `nothing is filled in until the shop is registered`() {
        assertEquals(CompanyDto(null, null, null, null), CompanyOrchestrator(testProperties()).company())
    }

    @Test
    fun `filled in values are trimmed, empty ones stay null`() {
        val properties = testProperties().copy(
            company = AppProperties.Company(legalName = " ", address = "Dorpsstraat 1, 1421 AB Uithoorn ", kvkNumber = " 12345678", vatNumber = ""),
        )

        assertEquals(CompanyDto(null, "Dorpsstraat 1, 1421 AB Uithoorn", "12345678", null), CompanyOrchestrator(properties).company())
    }
}
