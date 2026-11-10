package com.emetain.pokemonwebshop.domain

import jakarta.persistence.CascadeType
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
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * An order. Names, prices and the address are copies from the moment of purchase, so later changes to a product or a
 * profile never change an order. Amounts are always in euro cents, including VAT, whatever currency the customer
 * chose for card prices.
 */
@Entity
@Table(
    name = "orders",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_orders_number", columnNames = ["number"]),
        UniqueConstraint(name = "uk_orders_access_token", columnNames = ["access_token"]),
        UniqueConstraint(name = "uk_orders_stripe_session_id", columnNames = ["stripe_session_id"]),
    ],
    indexes = [
        Index(name = "idx_orders_user_created", columnList = "user_id, created_at"),
        Index(name = "idx_orders_status_created", columnList = "status, created_at"),
        Index(name = "idx_orders_paid_at", columnList = "paid_at"),
        Index(name = "idx_orders_cart_status", columnList = "cart_id, status"),
        Index(name = "idx_orders_stock_reserved_until", columnList = "stock_state, reserved_until"),
        Index(name = "idx_orders_email", columnList = "email"),
        Index(name = "idx_orders_drop_id", columnList = "drop_id"),
        Index(name = "idx_orders_drop_entry_id", columnList = "drop_entry_id"),
    ],
)
class Order(
    /** What the customer sees, such as `UT-7K3M9Q`. Random, so it says nothing about how much the shop sells. */
    @Column(nullable = false, length = 20)
    val number: String,

    /** Null for a guest. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User?,

    @Column(nullable = false, length = 254)
    val email: String,

    /** The profile name of a customer, or the name a guest filled in. Addresses have no name of their own. */
    @Column(nullable = false, length = 100)
    val customerName: String,

    @Column(nullable = false, length = 100)
    val street: String,

    @Column(nullable = false, length = 20)
    val houseNumber: String,

    @Column(nullable = false, length = 10)
    val postcode: String,

    @Column(nullable = false, length = 100)
    val city: String,

    @Column(nullable = false, length = 2)
    val country: String,

    @Column(nullable = false)
    val subtotalCents: Long,

    @Column(nullable = false)
    val shippingCents: Long,

    @Column(nullable = false)
    val totalCents: Long,

    /** The VAT included in [totalCents]. */
    @Column(nullable = false)
    val vatCents: Long,

    /** The delivery time of the shipping rate at the time of the order. */
    @Column(length = 200)
    val deliveryTime: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val shipmentPreference: ShipmentPreference,

    /** Opens the order for a guest, in the link on the success page and in mails. */
    @Column(nullable = false, length = 64)
    val accessToken: String,

    /** The cart the order came from: paid products leave it, and a new checkout replaces an unpaid one. */
    @Column(name = "cart_id")
    val cartId: Long?,

    @Column(nullable = false, updatable = false)
    val termsAcceptedAt: Instant,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING_PAYMENT,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var stockState: OrderStockState = OrderStockState.RESERVED,

    /** Until when the checkout holds the stock. */
    @Column
    var reservedUntil: Instant? = null,

    @Column(length = 255)
    var stripeSessionId: String? = null,

    /** When Stripe stops accepting payment for the session by itself. */
    @Column
    var stripeSessionExpiresAt: Instant? = null,

    @Column(length = 255)
    var stripePaymentIntentId: String? = null,

    /** How the customer paid, as Stripe names it: `ideal`, `bancontact`, `card`. */
    @Column(length = 40)
    var paymentMethod: String? = null,

    @Column
    var paidAt: Instant? = null,

    @Column
    var shippedAt: Instant? = null,

    @Column
    var deliveredAt: Instant? = null,

    @Column
    var cancelledAt: Instant? = null,

    @Column(nullable = false)
    var refundedCents: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var carrier: Carrier? = null,

    @Column(length = 100)
    var trackingCode: String? = null,

    /** Where the customer follows the parcel. Built from the code for PostNL, pasted by the admin for others. */
    @Column(length = 500)
    var trackingUrl: String? = null,

    /** The drop this order was made in. Its stock comes from the drop, not from the normal stock. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drop_id")
    val drop: Drop? = null,

    /** The waiting room entry of a drop order: paid products leave its drop cart, a new checkout replaces this one. */
    @Column(name = "drop_entry_id")
    val dropEntryId: Long? = null,

    /** For drop limits: the email without dots or a +tag where those make no difference. See `DropRules.emailKey`. */
    @Column(length = 254)
    val emailKey: String? = null,

    /** For drop limits: country, postcode and house number written one way. See `DropRules.addressKey`. */
    @Column(length = 200)
    val addressKey: String? = null,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("id ASC")
    val lines: MutableList<OrderLine> = mutableListOf(),

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    val history: MutableList<OrderStatusChange> = mutableListOf(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    val hasPreOrders: Boolean get() = lines.any { it.preOrder }

    val hasInStock: Boolean get() = lines.any { !it.preOrder }

    /** What can still be paid back. */
    val refundableCents: Long get() = if (paidAt == null) 0 else totalCents - refundedCents
}
