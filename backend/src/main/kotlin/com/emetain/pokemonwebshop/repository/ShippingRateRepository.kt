package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.ShippingRate
import org.springframework.data.jpa.repository.JpaRepository

interface ShippingRateRepository : JpaRepository<ShippingRate, Long> {

    fun findByCountry(country: String): ShippingRate?

    fun findAllByOrderByCountryAsc(): List<ShippingRate>
}
