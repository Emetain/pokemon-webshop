package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.ReviewStatus
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminReviewDto
import com.emetain.pokemonwebshop.dto.AdminReviewPageDto
import com.emetain.pokemonwebshop.dto.OwnReviewDto
import com.emetain.pokemonwebshop.dto.ProductReviewsDto
import com.emetain.pokemonwebshop.dto.ReviewDto
import com.emetain.pokemonwebshop.dto.ReviewRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.service.ReviewService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ReviewOrchestrator(
    private val reviewService: ReviewService,
    private val productRepository: ProductRepository,
) {

    @Transactional(readOnly = true)
    fun forProduct(slug: String, viewer: User?, page: Int): ProductReviewsDto {
        val product = activeProduct(slug)
        val summary = reviewService.summary(product.id)
        val reviews = reviewService.approved(product.id, page)
        val own = viewer?.let { reviewService.own(product.id, it) }
        return ProductReviewsDto(
            count = summary.count,
            averageRating = summary.average,
            reviews = reviews.content.map(ReviewDto::from),
            page = reviews.number,
            totalPages = reviews.totalPages,
            canReview = viewer != null && reviewService.hasBought(product.id, viewer),
            own = own?.let { OwnReviewDto(it.rating, it.title, it.text, it.status.name, it.updatedAt) },
        )
    }

    @Transactional
    fun write(slug: String, viewer: User, request: ReviewRequest): ProductReviewsDto {
        reviewService.write(activeProduct(slug), viewer, request)
        return forProduct(slug, viewer, 0)
    }

    @Transactional(readOnly = true)
    fun forAdmin(status: String?, page: Int): AdminReviewPageDto {
        val wanted = status?.takeIf { it.isNotBlank() }?.let {
            runCatching { ReviewStatus.valueOf(it) }.getOrElse { throw ApiException.validation("Kies een geldige status.") }
        }
        val result = reviewService.forAdmin(wanted, page)
        return AdminReviewPageDto(result.content.map(AdminReviewDto::from), result.number, result.totalPages, result.totalElements, reviewService.pendingCount())
    }

    @Transactional
    fun moderate(reviewId: Long, approve: Boolean): AdminReviewDto =
        AdminReviewDto.from(reviewService.moderate(reviewId, if (approve) ReviewStatus.APPROVED else ReviewStatus.REJECTED))

    fun delete(reviewId: Long) = reviewService.delete(reviewId)

    private fun activeProduct(slug: String) = productRepository.findBySlug(slug)?.takeIf { it.status == ProductStatus.ACTIVE }
        ?: throw ApiException.notFound("We konden dit product niet vinden.")
}
