package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.SUPPORTED_COUNTRIES
import com.emetain.pokemonwebshop.domain.ShippingRate
import com.emetain.pokemonwebshop.dto.ShippingRateRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.ShippingRateRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class ShippingRateService(
    private val repository: ShippingRateRepository,
    private val properties: AppProperties,
    private val clock: Clock,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Every country the shop ships to needs a rate, or nobody there can check out. A country without one gets the
     * default from `app.checkout.default-rates`, which the admin then changes. Rates that exist are never touched.
     */
    @Transactional
    override fun run(args: ApplicationArguments) {
        for (country in SUPPORTED_COUNTRIES.sorted()) {
            if (repository.findByCountry(country) != null) continue
            val default = properties.checkout.defaultRates[country] ?: continue
            repository.save(
                ShippingRate(
                    country = country,
                    priceCents = default.priceCents,
                    freeFromCents = default.freeFromCents,
                    deliveryTime = default.deliveryTime,
                    updatedAt = clock.instant(),
                ),
            )
            log.info("Created the default shipping rate for {}", country)
        }
    }

    @Transactional(readOnly = true)
    fun all(): List<ShippingRate> = repository.findAllByOrderByCountryAsc().filter { it.country in SUPPORTED_COUNTRIES }

    /** The rate customers in [country] pay, or null when the shop doesn't ship there. */
    @Transactional(readOnly = true)
    fun active(country: String): ShippingRate? = repository.findByCountry(country)?.takeIf { it.active }

    @Transactional
    fun update(country: String, request: ShippingRateRequest): ShippingRate {
        if (country !in SUPPORTED_COUNTRIES) throw ApiException.notFound("We verzenden niet naar dit land.")
        if (request.freeFromCents != null && request.freeFromCents <= 0) {
            throw ApiException.validation("Vul een bedrag voor gratis verzending in, of laat het leeg.")
        }
        val rate = repository.findByCountry(country)
            ?: repository.save(ShippingRate(country, request.priceCents, null, request.deliveryTime.trim(), updatedAt = clock.instant()))
        rate.priceCents = request.priceCents
        rate.freeFromCents = request.freeFromCents
        rate.deliveryTime = request.deliveryTime.trim()
        rate.active = request.active
        rate.updatedAt = clock.instant()
        return rate
    }
}
