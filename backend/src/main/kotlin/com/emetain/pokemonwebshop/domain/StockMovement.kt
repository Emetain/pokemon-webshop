package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * One change to the stock of a product. [change] is what changed: the reserved quantity for a
 * reservation or release, the quantity on hand for everything else. The numbers after the change are kept too,
 * so the log reads without adding up.
 */
@Entity
@Table(
    name = "stock_movements",
    indexes = [
        Index(name = "idx_stock_movements_product_created", columnList = "product_id, created_at"),
        Index(name = "idx_stock_movements_created_at", columnList = "created_at"),
    ],
)
class StockMovement(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @Column(name = "quantity_change", nullable = false)
    val change: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val reason: StockReason,

    @Column(nullable = false)
    val onHandAfter: Int,

    @Column(nullable = false)
    val reservedAfter: Int,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(length = 500)
    val note: String? = null,

    /** Who made the change. Null for changes the shop made itself, such as a sale. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id")
    val adminUser: User? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
