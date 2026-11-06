package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.repository.ExchangeRateRepository
import com.emetain.pokemonwebshop.service.ExchangeRateService
import com.emetain.pokemonwebshop.service.provider.CatalogHttpClient
import com.emetain.pokemonwebshop.service.provider.EcbRates
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class ExchangeRateIntegrationTest : IntegrationTest() {

    @MockBean
    private lateinit var http: CatalogHttpClient

    @Autowired
    private lateinit var exchangeRateService: ExchangeRateService

    @Autowired
    private lateinit var exchangeRateRepository: ExchangeRateRepository

    @BeforeEach
    fun clearRates() {
        exchangeRateRepository.deleteAll()
    }

    private fun ecbAnswers(xml: String) {
        whenever(http.get<EcbRates.Published>(any(), any(), any())).thenAnswer { call ->
            call.getArgument<(InputStream) -> EcbRates.Published>(2)(xml.byteInputStream())
        }
    }

    private fun file(date: String, usd: String) =
        """<Envelope><Cube><Cube time="$date"><Cube currency="USD" rate="$usd"/></Cube></Cube></Envelope>"""

    @Test
    fun `stores the rate and the catalog serves it without a login`() {
        ecbAnswers(file("2026-11-05", "1.1224"))

        exchangeRateService.refresh()

        mockMvc.perform(get("/v1/catalog/exchange-rates"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].currency").value("USD"))
            .andExpect(jsonPath("$[0].perEuro").value(1.1224))
            .andExpect(jsonPath("$[0].date").value("2026-11-05"))
    }

    @Test
    fun `a new rate replaces the old one`() {
        ecbAnswers(file("2026-11-05", "1.1224"))
        exchangeRateService.refresh()
        ecbAnswers(file("2026-11-06", "1.2"))

        exchangeRateService.refresh()

        val stored = exchangeRateRepository.findAll().single()
        assertEquals(0, BigDecimal("1.2").compareTo(stored.perEuro))
        assertEquals(LocalDate.of(2026, 11, 6), stored.rateDate)
    }

    @Test
    fun `a file without our currency keeps the rate we have`() {
        ecbAnswers(file("2026-11-05", "1.1224"))
        exchangeRateService.refresh()
        ecbAnswers("""<Envelope><Cube><Cube time="2026-11-06"><Cube currency="GBP" rate="0.8"/></Cube></Cube></Envelope>""")

        assertThrows<IllegalArgumentException> { exchangeRateService.refresh() }

        assertEquals(LocalDate.of(2026, 11, 5), exchangeRateRepository.findById(Currency.USD).orElseThrow().rateDate)
    }

    @Test
    fun `without a rate the list is empty`() {
        mockMvc.perform(get("/v1/catalog/exchange-rates"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
