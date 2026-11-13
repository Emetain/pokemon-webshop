package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductReview
import com.emetain.pokemonwebshop.domain.ReviewStatus
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.ReviewRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.emetain.pokemonwebshop.repository.ProductReviewRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** How many approved reviews a product has, and their average number of stars. */
data class ReviewSummary(val count: Int, val average: Double?)

@Service
class ReviewService(
    private val reviewRepository: ProductReviewRepository,
    private val orderRepository: OrderRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun summary(productId: Long): ReviewSummary {
        val row = reviewRepository.summary(productId).firstOrNull()
        val count = (row?.get(0) as Number?)?.toInt() ?: 0
        val average = (row?.get(1) as Number?)?.toDouble()?.let { Math.round(it * 10) / 10.0 }
        return ReviewSummary(count, average.takeIf { count > 0 })
    }

    @Transactional(readOnly = true)
    fun approved(productId: Long, page: Int): Page<ProductReview> =
        reviewRepository.findByProductAndStatus(productId, ReviewStatus.APPROVED, PageRequest.of(page.coerceAtLeast(0), PAGE_SIZE))

    @Transactional(readOnly = true)
    fun own(productId: Long, user: User): ProductReview? = reviewRepository.findByProductIdAndUserId(productId, user.id)

    @Transactional(readOnly = true)
    fun hasBought(productId: Long, user: User): Boolean = orderRepository.hasBought(productId, user.id)

    /** Writes or changes the customer's review. Either way it waits for an admin again. */
    @Transactional
    fun write(product: Product, user: User, request: ReviewRequest): ProductReview {
        if (!orderRepository.hasBought(product.id, user.id)) {
            throw ApiException(HttpStatus.FORBIDDEN, "not_a_buyer", "Je kunt een review schrijven als je dit product bij ons hebt gekocht.")
        }
        if (request.rating !in 1..MAX_STARS) throw ApiException.validation("Kies 1 tot 5 sterren.")
        val text = request.text.trim()
        if (text.length < MIN_TEXT) throw ApiException.validation("Schrijf minstens $MIN_TEXT tekens.")
        val now = clock.instant()
        val review = reviewRepository.findByProductIdAndUserId(product.id, user.id)
            ?: ProductReview(product = product, user = user, rating = request.rating, text = text, authorName = "", createdAt = now, updatedAt = now)
        review.rating = request.rating
        review.title = request.title?.trim()?.takeIf { it.isNotEmpty() }
        review.text = text
        review.authorName = ReviewRules.firstName(user.displayName)
        review.status = ReviewStatus.PENDING
        review.moderatedAt = null
        review.updatedAt = now
        return reviewRepository.save(review)
    }

    @Transactional(readOnly = true)
    fun forAdmin(status: ReviewStatus?, page: Int): Page<ProductReview> =
        reviewRepository.findForAdmin(status, PageRequest.of(page.coerceAtLeast(0), ADMIN_PAGE_SIZE))

    @Transactional(readOnly = true)
    fun pendingCount(): Long = reviewRepository.countByStatus(ReviewStatus.PENDING)

    @Transactional
    fun moderate(reviewId: Long, status: ReviewStatus): ProductReview {
        val review = find(reviewId)
        review.status = status
        review.moderatedAt = clock.instant()
        return review
    }

    @Transactional
    fun delete(reviewId: Long) = reviewRepository.delete(find(reviewId))

    private fun find(reviewId: Long) =
        reviewRepository.findById(reviewId).orElseThrow { ApiException.notFound("We konden deze review niet vinden.") }

    private companion object {
        const val PAGE_SIZE = 10
        const val ADMIN_PAGE_SIZE = 25
        const val MAX_STARS = 5
        const val MIN_TEXT = 10
    }
}

object ReviewRules {
    /** "Jay Nagtegaal" shows as "Jay". A name that is only spaces shows as "Klant". */
    fun firstName(displayName: String): String =
        displayName.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotEmpty() }?.take(50) ?: "Klant"
}
