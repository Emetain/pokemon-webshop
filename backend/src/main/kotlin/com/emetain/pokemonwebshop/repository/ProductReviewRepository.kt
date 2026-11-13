package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.ProductReview
import com.emetain.pokemonwebshop.domain.ReviewStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProductReviewRepository : JpaRepository<ProductReview, Long> {

    @Query(
        """
        select r from ProductReview r where r.product.id = :productId and r.status = :status
        order by r.createdAt desc, r.id desc
        """,
    )
    fun findByProductAndStatus(productId: Long, status: ReviewStatus, pageable: Pageable): Page<ProductReview>

    fun findByProductIdAndUserId(productId: Long, userId: Long): ProductReview?

    /** Count and average of the approved reviews of a product. */
    @Query("select count(r), avg(r.rating) from ProductReview r where r.product.id = :productId and r.status = 'APPROVED'")
    fun summary(productId: Long): List<Array<Any?>>

    @EntityGraph(attributePaths = ["product", "user"])
    @Query("select r from ProductReview r where (:status is null or r.status = :status) order by r.createdAt desc, r.id desc")
    fun findForAdmin(status: ReviewStatus?, pageable: Pageable): Page<ProductReview>

    fun countByStatus(status: ReviewStatus): Long
}
