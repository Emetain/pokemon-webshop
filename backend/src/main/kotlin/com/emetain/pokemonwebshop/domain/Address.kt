package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "addresses",
    indexes = [Index(name = "idx_addresses_user_id", columnList = "user_id")],
)
class Address(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false, length = 100)
    var street: String,

    @Column(nullable = false, length = 20)
    var houseNumber: String,

    @Column(nullable = false, length = 10)
    var postcode: String,

    @Column(nullable = false, length = 100)
    var city: String,

    @Column(nullable = false, length = 2)
    var country: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
