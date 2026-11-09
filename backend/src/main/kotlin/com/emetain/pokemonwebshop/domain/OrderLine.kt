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
import java.time.LocalDate

/** One product in an order, with copies of its name and price at the moment of purchase. */
@Entity
@Table(
    name = "order_lines",
    indexes = [
        Index(name = "idx_order_lines_order_id", columnList = "order_id"),
        Index(name = "idx_order_lines_product_id", columnList = "product_id"),
    ],
)
class OrderLine(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @Column(nullable = false, length = 255)
    val productName: String,

    @Column(nullable = false, length = 200)
    val productSlug: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val productType: ProductType,

    @Column(length = 500)
    val imageUrl: String?,

    /** The image is a card from the catalog, which may never be cropped or covered. */
    @Column(nullable = false)
    val imageIsCard: Boolean,

    /** What one cost, sale price included. */
    @Column(nullable = false)
    val unitPriceCents: Long,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false)
    val lineTotalCents: Long,

    @Column(nullable = false)
    val preOrder: Boolean,

    /** For a pre-order: when it is expected to go out. */
    @Column
    val shipsFrom: LocalDate?,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
