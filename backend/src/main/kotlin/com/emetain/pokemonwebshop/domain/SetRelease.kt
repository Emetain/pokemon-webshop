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
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * An upcoming set on the release calendar, managed in admin. Upcoming sets are often not in the card catalog yet,
 * so a release has its own name and date. It can point at a set of the catalog once that exists, and at the
 * pre-order products of the shop.
 */
@Entity
@Table(
    name = "set_releases",
    indexes = [Index(name = "idx_set_releases_release_date", columnList = "release_date")],
)
class SetRelease(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var game: Game,

    @Column(nullable = false, length = 150)
    var name: String,

    @Column(nullable = false)
    var releaseDate: LocalDate,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,

    /** The date is expected, not announced: the calendar shows the month with "verwacht". */
    @Column(nullable = false)
    var tentative: Boolean = false,

    @Column(length = 1000)
    var description: String? = null,

    @Column(length = 500)
    var imageUrl: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_id")
    var cardSet: CardSet? = null,

    /** Hidden from the calendar while false. */
    @Column(nullable = false)
    var published: Boolean = true,

    /** The pre-orders of this release, in the order the admin chose. */
    @ManyToMany
    @JoinTable(
        name = "set_release_products",
        joinColumns = [JoinColumn(name = "release_id")],
        inverseJoinColumns = [JoinColumn(name = "product_id")],
    )
    @OrderColumn(name = "position")
    val products: MutableList<Product> = mutableListOf(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
