package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

/** The ECB reference rate: what one euro is worth in [currency]. Only for showing prices, never for payments. */
@Entity
@Table(name = "exchange_rates")
class ExchangeRate(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 3)
    val currency: Currency,

    @Column(nullable = false, precision = 12, scale = 6)
    var perEuro: BigDecimal,

    @Column(nullable = false)
    var rateDate: LocalDate,
)
