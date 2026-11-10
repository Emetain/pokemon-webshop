package com.emetain.pokemonwebshop.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.ColumnDefault
import java.time.Instant

/**
 * A limited, timed release. Its products have stock of their own, taken from the normal stock when the admin
 * allocates it. Every change to that stock, the waiting room and the limits goes through a row lock on the drop, so
 * two buyers at the same moment can never sell more than was allocated.
 */
@Entity
@Table(
    name = "drops",
    uniqueConstraints = [UniqueConstraint(name = "uk_drops_slug", columnNames = ["slug"])],
    indexes = [Index(name = "idx_drops_status_starts_at", columnList = "status, starts_at")],
)
class Drop(
    @Column(nullable = false, length = 120)
    var name: String,

    @Column(nullable = false, length = 140)
    var slug: String,

    @Column(nullable = false)
    var startsAt: Instant,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,

    /** Plain text. Line breaks are kept. */
    @Column(length = 5000)
    var description: String? = null,

    @Column(length = 500)
    var bannerUrl: String? = null,

    /** Null: the drop runs until everything is sold or the admin ends it. */
    @Column
    var endsAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: DropStatus = DropStatus.DRAFT,

    /** Only customers with an account can join. On by default: accounts make bots and repeat orders harder. */
    @Column(nullable = false)
    var requiresAccount: Boolean = true,

    /** The most items one customer buys in this drop, all products together. Null: only the limits per product. */
    @Column
    var maxPerCustomer: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var leftover: DropLeftover = DropLeftover.RETURN_TO_STOCK,

    /** The drop gets a slide in the hero carousel on the home page while it is upcoming or live. */
    @Column(nullable = false)
    var showInHero: Boolean = true,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'BLACK'")
    var heroColour: DropHeroColour = DropHeroColour.BLACK,

    /** How many people the waiting room lets through per minute. The admin can change it during the drop. */
    @Column(nullable = false)
    var admitPerMinute: Int,

    /** Nobody new is let through while this is on. Whoever is in already keeps shopping. */
    @Column(nullable = false)
    var queuePaused: Boolean = false,

    @Column
    var startedAt: Instant? = null,

    @Column
    var endedAt: Instant? = null,

    /** Why the drop ended, for the admin: the end time, sold out, or ended early. */
    @Column(length = 200)
    var endReason: String? = null,

    /** When everyone in the waiting room got a random place. Null until the start. */
    @Column
    var shuffledAt: Instant? = null,

    /** How many people were in the waiting room at the start and got a random place. */
    @Column(nullable = false)
    var shuffledCount: Int = 0,

    /** The place the next person who joins after the start gets. */
    @Column(nullable = false)
    var nextPosition: Long = 1,

    /** Everyone up to this place may shop. The scheduler moves it up in batches. */
    @Column(nullable = false)
    var admittedUpTo: Long = 0,

    /** Up to when the admission rate has been used. Moves forward with each batch. */
    @Column
    var admittedUntil: Instant? = null,

    @Column
    var remindersSentAt: Instant? = null,

    /** When the unsold stock went back to the normal stock. Stock released after this goes straight back too. */
    @Column
    var leftoverReturnedAt: Instant? = null,

    @OneToMany(mappedBy = "drop", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    val products: MutableList<DropProduct> = mutableListOf(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    /** Unsold stock goes back to the normal stock from now on, also stock released by an unpaid checkout. */
    val returnsReleasedStock: Boolean get() = status == DropStatus.ENDED && leftoverReturnedAt != null
}
