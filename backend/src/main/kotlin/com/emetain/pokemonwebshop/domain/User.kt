package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true, length = 254)
    var email: String,

    @Column(nullable = false, length = 100)
    var passwordHash: String,

    @Column(nullable = false, length = 100)
    var displayName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: Role = Role.CUSTOMER,

    @Column(nullable = false, length = 2)
    var country: String = DEFAULT_COUNTRY,

    /** The currency card prices are shown in. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    var currency: Currency = Currency.EUR,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
