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
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * A Premium subscription at Stripe (phase 11), as Stripe last told the shop through a verified webhook. The shop
 * never decides on its own that a subscription was paid: only Stripe's own answer, asked for after a signed event,
 * changes this row and the user's Premium.
 */
@Entity
@Table(
    name = "premium_subscriptions",
    uniqueConstraints = [UniqueConstraint(name = "uk_premium_subscriptions_stripe_id", columnNames = ["stripe_subscription_id"])],
    indexes = [Index(name = "idx_premium_subscriptions_user", columnList = "user_id")],
)
class PremiumSubscription(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, length = 255)
    val stripeSubscriptionId: String,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var plan: PremiumPlan? = null,

    /** Stripe's status: active, trialing, past_due, canceled, unpaid, incomplete, incomplete_expired or paused. */
    @Column(nullable = false, length = 30)
    var status: String,

    @Column
    var currentPeriodEnd: Instant? = null,

    /** The customer cancelled: Premium stays until [currentPeriodEnd] and doesn't renew. */
    @Column(nullable = false)
    var cancelAtPeriodEnd: Boolean = false,

    /** When the customer agreed that Premium starts right away and the 14-day withdrawal right ends with that. */
    @Column
    var consentAt: Instant? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
