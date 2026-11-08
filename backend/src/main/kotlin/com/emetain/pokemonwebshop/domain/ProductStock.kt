package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import jakarta.persistence.Version

/**
 * The stock of one product. Only `StockService` changes it, and it always writes a [StockMovement].
 * [version] makes a second change that started from the same numbers fail, so two people can't buy the last item.
 */
@Entity
@Table(name = "product_stock")
class ProductStock(
    @Column(nullable = false)
    var onHand: Int = 0,

    /** Held for checkouts that aren't paid yet. Can't be sold to anyone else. */
    @Column(nullable = false)
    var reserved: Int = 0,

    @Version
    @Column(nullable = false)
    var version: Long = 0,

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_stock_seq")
    @SequenceGenerator(name = "product_stock_seq", sequenceName = "product_stock_seq", allocationSize = 50)
    val id: Long = 0,
) {
    /** What can still be sold. */
    val available: Int get() = onHand - reserved
}
