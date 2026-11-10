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
import org.hibernate.annotations.DynamicUpdate
import java.time.Instant

/**
 * One person in the waiting room of a drop: a customer, or a guest behind the `drop_visitor` cookie when the drop
 * doesn't require an account. Whoever joined before the start gets a random [position] at the start, later
 * arrivals get the next one. Once the drop lets their place through, they get an access token for a limited time,
 * and their drop cart lives here.
 *
 * Only changed columns are written: a ping that updates [lastSeenAt] at the same moment as the shuffle sets
 * [position] must not undo it.
 */
@Entity
@DynamicUpdate
@Table(
    name = "drop_queue_entries",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_drop_queue_entries_drop_user", columnNames = ["drop_id", "user_id"]),
        UniqueConstraint(name = "uk_drop_queue_entries_drop_visitor", columnNames = ["drop_id", "visitor_token_hash"]),
        UniqueConstraint(name = "uk_drop_queue_entries_access_token", columnNames = ["access_token_hash"]),
    ],
    indexes = [Index(name = "idx_drop_queue_entries_drop_position", columnList = "drop_id, position")],
)
class DropQueueEntry(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drop_id", nullable = false)
    val drop: Drop,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User?,

    /** The hash of the guest's `drop_visitor` cookie. Null for a customer. */
    @Column(length = 64)
    val visitorTokenHash: String?,

    @Column(nullable = false)
    var joinedAt: Instant,

    /** The waiting room only lets through people who are still there. */
    @Column(nullable = false)
    var lastSeenAt: Instant,

    /** Null until the start for whoever joined before it. */
    @Column
    var position: Long? = null,

    @Column
    var admittedAt: Instant? = null,

    @Column(length = 64)
    var accessTokenHash: String? = null,

    @Column
    var accessExpiresAt: Instant? = null,

    /** How often this person got in line: more than once after their time ran out. */
    @Column(nullable = false)
    var joinCount: Int = 1,

    @OneToMany(mappedBy = "entry", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("addedAt ASC, id ASC")
    val items: MutableList<DropCartItem> = mutableListOf(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    fun hasAccess(now: Instant): Boolean = accessExpiresAt?.isAfter(now) == true
}

/** A product in someone's drop cart. Holds no stock: the drop checkout reserves it. */
@Entity
@Table(
    name = "drop_cart_items",
    uniqueConstraints = [UniqueConstraint(name = "uk_drop_cart_items_entry_product", columnNames = ["entry_id", "drop_product_id"])],
    indexes = [Index(name = "idx_drop_cart_items_drop_product_id", columnList = "drop_product_id")],
)
class DropCartItem(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    val entry: DropQueueEntry,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drop_product_id", nullable = false)
    val dropProduct: DropProduct,

    @Column(nullable = false)
    var quantity: Int,

    @Column(nullable = false, updatable = false)
    val addedAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
