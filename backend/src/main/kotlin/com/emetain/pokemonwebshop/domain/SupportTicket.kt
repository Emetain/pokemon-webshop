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

/** `OPEN`: waiting for the shop. `ANSWERED`: the shop replied last. `CLOSED`: done, until the customer writes again. */
enum class TicketStatus { OPEN, ANSWERED, CLOSED }

/**
 * A question from a customer. The customer follows it with a secret link from the mail, logged in or not, and the
 * shop answers it in admin. Every message goes out by mail as well.
 */
@Entity
@Table(
    name = "support_tickets",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_support_tickets_number", columnNames = ["number"]),
        UniqueConstraint(name = "uk_support_tickets_access_token", columnNames = ["access_token"]),
    ],
    indexes = [
        Index(name = "idx_support_tickets_status_updated", columnList = "status, updated_at"),
        Index(name = "idx_support_tickets_user_id", columnList = "user_id"),
    ],
)
class SupportTicket(
    /** Shown to the customer and in mail subjects, such as `T-7K3M9Q`. */
    @Column(nullable = false, length = 20, updatable = false)
    val number: String,

    /** The secret in the link to the ticket. Whoever has it can read and answer the ticket. */
    @Column(nullable = false, length = 64, updatable = false)
    val accessToken: String,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(nullable = false, length = 254)
    val email: String,

    @Column(nullable = false, length = 150)
    val subject: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,

    /** The account it was sent from, so it shows under "Mijn tickets". Null for a guest. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User? = null,

    @Column(length = 20)
    val orderNumber: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TicketStatus = TicketStatus.OPEN,

    @OneToMany(mappedBy = "ticket", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("createdAt asc, id asc")
    val messages: MutableList<SupportMessage> = mutableListOf(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)

/** One message in a ticket, from the customer or from the shop. */
@Entity
@Table(name = "support_messages", indexes = [Index(name = "idx_support_messages_ticket_id", columnList = "ticket_id")])
class SupportMessage(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    val ticket: SupportTicket,

    /** True for an answer from the shop. */
    @Column(nullable = false)
    val fromShop: Boolean,

    @Column(nullable = false, length = 10000)
    val body: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    /** The admin who answered. Customers see "Poketopia", not the name. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    val author: User? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
