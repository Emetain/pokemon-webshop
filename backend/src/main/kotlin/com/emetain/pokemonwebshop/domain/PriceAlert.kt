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
import java.time.LocalDate

/**
 * What a price alert waits for (phase 12). The owner's two: "mail me when it drops below €…", meant for wishlist cards,
 * and "when it rises or falls more than …%", meant for owned cards. Both work on any card.
 */
enum class PriceAlertKind { BELOW, CHANGE }

/**
 * A customer's price alert on one variant of a card (Premium, phase 12). Checked once a day after the sync by
 * [com.emetain.pokemonwebshop.service.PriceAlertJob]; the rules are in [com.emetain.pokemonwebshop.service.PriceAlertRules].
 */
@Entity
@Table(
    name = "price_alerts",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_price_alerts_user_card_variant_kind", columnNames = ["user_id", "card_id", "variant", "kind"]),
    ],
    indexes = [Index(name = "idx_price_alerts_card", columnList = "card_id")],
)
class PriceAlert(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    val card: Card,

    @Column(nullable = false, length = 30)
    val variant: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val kind: PriceAlertKind,

    /** [PriceAlertKind.BELOW]: the price to go under, in [currency]. */
    @Column
    var thresholdCents: Long? = null,

    /** The currency of [thresholdCents]: the customer's currency when they set it. */
    @Enumerated(EnumType.STRING)
    @Column(length = 3)
    var currency: Currency? = null,

    /** [PriceAlertKind.CHANGE]: how many percent up or down. */
    @Column
    var percent: Int? = null,

    /** [PriceAlertKind.CHANGE]: the price the change is measured from: when it was set, then each time it went off. */
    @Column
    var baselineCents: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 3)
    var baselineCurrency: Currency? = null,

    /**
     * [PriceAlertKind.BELOW]: may go off. It goes off once when the price drops under the line, and is armed again
     * when the price is back at or above it, so a price that stays low doesn't send a mail every day.
     */
    @Column(nullable = false)
    var armed: Boolean = true,

    /** The last day it went off: a customer gets at most one alert mail a day. */
    @Column
    var lastTriggeredOn: LocalDate? = null,

    /** The hash of the link in the last alert mail that switches this alert off without logging in. */
    @Column(length = 64)
    var offTokenHash: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
