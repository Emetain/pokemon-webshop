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
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.Instant

/**
 * A product in a drop, with stock of its own. [allocated] left the normal stock with a `DROP_ALLOCATION` movement.
 * Only `DropStockService` changes the numbers, under the lock on the drop. [version] is a second safety net.
 */
@Entity
@Table(
    name = "drop_products",
    uniqueConstraints = [UniqueConstraint(name = "uk_drop_products_drop_product", columnNames = ["drop_id", "product_id"])],
    indexes = [Index(name = "idx_drop_products_product_id", columnList = "product_id")],
)
class DropProduct(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drop_id", nullable = false)
    val drop: Drop,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @Column(nullable = false)
    var position: Int,

    /** Taken from the normal stock for this drop. */
    @Column(nullable = false)
    var allocated: Int = 0,

    /** Held for drop checkouts that aren't paid yet. */
    @Column(nullable = false)
    var reserved: Int = 0,

    @Column(nullable = false)
    var sold: Int = 0,

    /** The most one customer buys of this product. Null: only the limit of the whole drop. */
    @Column
    var maxPerCustomer: Int? = null,

    /** The price in the drop, in euro cents including VAT. Null: the shop price. */
    @Column
    var priceCents: Long? = null,

    /** When the last one was paid for, for the report. */
    @Column
    var soldOutAt: Instant? = null,

    @Version
    @Column(nullable = false)
    var version: Long = 0,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    /** What can still go into a checkout. */
    val available: Int get() = allocated - reserved - sold

    /** Nothing left and nothing held that could come back. */
    val soldOut: Boolean get() = available <= 0 && reserved <= 0
}
