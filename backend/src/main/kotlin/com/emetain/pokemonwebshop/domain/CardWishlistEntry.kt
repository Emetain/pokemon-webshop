package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/** A card a customer wants. Separate from the wishlist for shop products. */
@Entity
@Table(
    name = "card_wishlist_entries",
    uniqueConstraints = [UniqueConstraint(name = "uk_card_wishlist_entries_user_card", columnNames = ["user_id", "card_id"])],
)
class CardWishlistEntry(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    val card: Card,

    @Column(nullable = false, updatable = false)
    val addedAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
