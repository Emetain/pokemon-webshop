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
import java.time.Instant

/** A shop product a customer wants. Separate from the wishlist for cards. */
@Entity
@Table(
    name = "product_wishlist_entries",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_product_wishlist_entries_user_product", columnNames = ["user_id", "product_id"]),
    ],
    indexes = [Index(name = "idx_product_wishlist_entries_product_id", columnList = "product_id")],
)
class ProductWishlistEntry(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @Column(nullable = false, updatable = false)
    val addedAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
