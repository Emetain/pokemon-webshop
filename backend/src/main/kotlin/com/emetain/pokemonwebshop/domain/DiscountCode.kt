package com.emetain.pokemonwebshop.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

enum class DiscountKind { PERCENTAGE, FIXED_AMOUNT }

/**
 * A code customers type at checkout, made in admin. It takes a percentage or a fixed amount off the products, never
 * off shipping. Empty [games] and [productTypes] mean every game and every kind of product. An order keeps a copy of
 * the code and the amount, so changing a code later never changes an order.
 */
@Entity
@Table(
    name = "discount_codes",
    uniqueConstraints = [UniqueConstraint(name = "uk_discount_codes_code", columnNames = ["code"])],
)
class DiscountCode(
    /** In capitals, such as `WELKOM10`. Customers can type it in any case. */
    @Column(nullable = false, length = 40)
    var code: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var kind: DiscountKind,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,

    /** 1 to 100, for [DiscountKind.PERCENTAGE]. */
    @Column
    var percent: Int? = null,

    /** In euro cents, for [DiscountKind.FIXED_AMOUNT]. */
    @Column
    var amountCents: Long? = null,

    /** The products in the order must cost at least this much, before the discount. */
    @Column
    var minimumOrderCents: Long? = null,

    @Column
    var startsAt: Instant? = null,

    @Column
    var endsAt: Instant? = null,

    /** How many orders may use it in all. Null is no limit. */
    @Column
    var maxUses: Int? = null,

    /** One order per account or email address. */
    @Column(nullable = false)
    var oncePerCustomer: Boolean = false,

    /** Drop prices are already special: a code only works in a drop when this is on. */
    @Column(nullable = false)
    var appliesToDrops: Boolean = false,

    @Column(nullable = false)
    var active: Boolean = true,

    /** For the admin only, such as "Instagram winactie november". */
    @Column(length = 200)
    var note: String? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "discount_code_games", joinColumns = [JoinColumn(name = "discount_code_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "game", nullable = false, length = 20)
    val games: MutableSet<Game> = mutableSetOf(),

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "discount_code_product_types", joinColumns = [JoinColumn(name = "discount_code_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 30)
    val productTypes: MutableSet<ProductType> = mutableSetOf(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
