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
 * One step in the history of an order: a new status, or a note on the current one, such as a track & trace code or a
 * partial refund. The customer sees the statuses, the admin also sees the notes.
 */
@Entity
@Table(
    name = "order_status_changes",
    indexes = [Index(name = "idx_order_status_changes_order_id", columnList = "order_id")],
)
class OrderStatusChange(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order,

    /** The status after this step. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: OrderStatus,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(length = 500)
    val note: String? = null,

    /** Null for steps the shop took itself, such as a payment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id")
    val adminUser: User? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
