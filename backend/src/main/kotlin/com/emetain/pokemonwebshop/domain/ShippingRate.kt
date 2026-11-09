package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/** What shipping to one country costs. The admin manages these. */
@Entity
@Table(
    name = "shipping_rates",
    uniqueConstraints = [UniqueConstraint(name = "uk_shipping_rates_country", columnNames = ["country"])],
)
class ShippingRate(
    @Column(nullable = false, length = 2)
    val country: String,

    /** In euro cents, including VAT. */
    @Column(nullable = false)
    var priceCents: Long,

    /** Shipping is free from this order amount on. Null: never free. */
    @Column
    var freeFromCents: Long? = null,

    /** Shown to the customer, such as "1 tot 2 werkdagen". */
    @Column(nullable = false, length = 200)
    var deliveryTime: String,

    /** Without an active rate, the shop doesn't ship to the country. */
    @Column(nullable = false)
    var active: Boolean = true,

    @Column(nullable = false)
    var updatedAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
