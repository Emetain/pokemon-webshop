package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/** "Mail me when back in stock". One mail, then [notifiedAt] is set. Signing up again starts over. */
@Entity
@Table(
    name = "stock_alerts",
    uniqueConstraints = [UniqueConstraint(name = "uk_stock_alerts_product_email", columnNames = ["product_id", "email"])],
)
class StockAlert(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @Column(nullable = false, length = 254)
    val email: String,

    @Column(nullable = false)
    var createdAt: Instant,

    @Column
    var notifiedAt: Instant? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
