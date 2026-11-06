package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.ExchangeRate
import com.emetain.pokemonwebshop.repository.ExchangeRateRepository
import com.emetain.pokemonwebshop.service.provider.CatalogHttpClient
import com.emetain.pokemonwebshop.service.provider.EcbRates
import org.springframework.stereotype.Service

/** Keeps the exchange rates up to date, so showing a price never has to call the ECB. */
@Service
class ExchangeRateService(
    private val http: CatalogHttpClient,
    private val exchangeRateRepository: ExchangeRateRepository,
    properties: AppProperties,
) {

    private val source = properties.catalog.ecb

    /** One at a time, because the sync of every game asks for this when it starts. */
    @Synchronized
    fun refresh() {
        val url = "${source.baseUrl}/stats/eurofxref/eurofxref-daily.xml"
        val published = http.get(url, source.requestDelay, EcbRates::parse)

        for (currency in Currency.entries - Currency.EUR) {
            val rate = requireNotNull(published.perEuro[currency.name]) { "The ECB file has no rate for $currency" }
            exchangeRateRepository.save(ExchangeRate(currency, rate, published.date))
        }
    }
}
