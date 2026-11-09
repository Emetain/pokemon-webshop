package com.emetain.pokemonwebshop.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * A shopping cart. A logged-in customer has one on the account, a guest one behind the `cart` cookie, of which only
 * the hash is stored. A cart holds no stock: checkout reserves it.
 */
@Entity
@Table(
    name = "carts",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_carts_user_id", columnNames = ["user_id"]),
        UniqueConstraint(name = "uk_carts_guest_token_hash", columnNames = ["guest_token_hash"]),
    ],
    indexes = [Index(name = "idx_carts_updated_at", columnList = "updated_at")],
)
class Cart(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User? = null,

    @Column(length = 64)
    val guestTokenHash: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,

    @OneToMany(mappedBy = "cart", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("addedAt ASC, id ASC")
    val items: MutableList<CartItem> = mutableListOf(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)

@Entity
@Table(
    name = "cart_items",
    uniqueConstraints = [UniqueConstraint(name = "uk_cart_items_cart_product", columnNames = ["cart_id", "product_id"])],
    indexes = [Index(name = "idx_cart_items_product_id", columnList = "product_id")],
)
class CartItem(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    val cart: Cart,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @Column(nullable = false)
    var quantity: Int,

    @Column(nullable = false, updatable = false)
    val addedAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
