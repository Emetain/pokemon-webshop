package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.ProductReview
import jakarta.validation.constraints.Size
import java.time.Instant

data class ReviewDto(
    val id: Long,
    val rating: Int,
    val title: String?,
    val text: String,
    /** First name only. */
    val authorName: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(review: ProductReview) =
            ReviewDto(review.id, review.rating, review.title, review.text, review.authorName, review.createdAt)
    }
}

/** The customer's own review, also while it waits for approval. */
data class OwnReviewDto(
    val rating: Int,
    val title: String?,
    val text: String,
    /** `PENDING`, `APPROVED` or `REJECTED`. */
    val status: String,
    val updatedAt: Instant,
)

data class ProductReviewsDto(
    val count: Int,
    val averageRating: Double?,
    /** Approved reviews, newest first, a page at a time. */
    val reviews: List<ReviewDto>,
    val page: Int,
    val totalPages: Int,
    /** Logged in and bought the product. */
    val canReview: Boolean,
    val own: OwnReviewDto?,
)

data class ReviewRequest(
    val rating: Int = 0,
    @field:Size(max = 100, message = "De titel mag maximaal 100 tekens hebben.")
    val title: String? = null,
    @field:Size(max = 1000, message = "Je review mag maximaal 1000 tekens hebben.")
    val text: String = "",
)

data class AdminReviewDto(
    val id: Long,
    val productName: String,
    val productSlug: String,
    val rating: Int,
    val title: String?,
    val text: String,
    val authorName: String,
    val customerEmail: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(review: ProductReview) = AdminReviewDto(
            id = review.id,
            productName = review.product.name,
            productSlug = review.product.slug,
            rating = review.rating,
            title = review.title,
            text = review.text,
            authorName = review.authorName,
            customerEmail = review.user.email,
            status = review.status.name,
            createdAt = review.createdAt,
            updatedAt = review.updatedAt,
        )
    }
}

data class AdminReviewPageDto(
    val reviews: List<AdminReviewDto>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
    val pendingCount: Long,
)
