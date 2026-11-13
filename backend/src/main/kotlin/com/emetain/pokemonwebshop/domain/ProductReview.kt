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

enum class ReviewStatus { PENDING, APPROVED, REJECTED }

/**
 * A star rating with a short text from a customer who bought the product. Only shows once an admin approved it.
 * One per customer per product: changing it sends it back for approval.
 */
@Entity
@Table(
    name = "product_reviews",
    uniqueConstraints = [UniqueConstraint(name = "uk_product_reviews_product_user", columnNames = ["product_id", "user_id"])],
    indexes = [
        Index(name = "idx_product_reviews_product_status", columnList = "product_id, status"),
        Index(name = "idx_product_reviews_status_created", columnList = "status, created_at"),
    ],
)
class ProductReview(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    /** 1 to 5 stars. */
    @Column(nullable = false)
    var rating: Int,

    @Column(nullable = false, length = 1000)
    var text: String,

    /** The first name of the customer when they wrote it. Never the full name: that is also the name on orders. */
    @Column(nullable = false, length = 50)
    var authorName: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,

    @Column(length = 100)
    var title: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReviewStatus = ReviewStatus.PENDING,

    @Column
    var moderatedAt: Instant? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
