package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.ExchangeRate
import org.springframework.data.jpa.repository.JpaRepository

interface ExchangeRateRepository : JpaRepository<ExchangeRate, Currency>
