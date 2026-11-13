package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminReviewDto
import com.emetain.pokemonwebshop.dto.AdminReviewPageDto
import com.emetain.pokemonwebshop.dto.ProductReviewsDto
import com.emetain.pokemonwebshop.dto.ReviewRequest
import com.emetain.pokemonwebshop.orchestration.ReviewOrchestrator
import com.emetain.pokemonwebshop.security.AdminOnly
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RateLimited
import com.emetain.pokemonwebshop.security.RequiresLogin
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Reviews of a product: everyone reads the approved ones, customers who bought it write one. */
@RestController
@RequestMapping("/v1/products/{slug}/reviews")
class ReviewController(
    private val reviewOrchestrator: ReviewOrchestrator,
) {

    @GetMapping
    fun list(@PathVariable slug: String, @CurrentUser viewer: User?, @RequestParam(defaultValue = "0") page: Int): ProductReviewsDto =
        reviewOrchestrator.forProduct(slug, viewer, page)

    @PutMapping
    @RequiresLogin
    @RateLimited("review")
    fun write(@PathVariable slug: String, @CurrentUser viewer: User, @Valid @RequestBody request: ReviewRequest): ProductReviewsDto =
        reviewOrchestrator.write(slug, viewer, request)
}

@RestController
@RequestMapping("/v1/admin/reviews")
@AdminOnly
class AdminReviewController(
    private val reviewOrchestrator: ReviewOrchestrator,
) {

    /** `status` is `PENDING`, `APPROVED`, `REJECTED`, or empty for all. */
    @GetMapping
    fun list(@RequestParam(required = false) status: String?, @RequestParam(defaultValue = "0") page: Int): AdminReviewPageDto =
        reviewOrchestrator.forAdmin(status, page)

    @PostMapping("/{id}/approve")
    fun approve(@PathVariable id: Long): AdminReviewDto = reviewOrchestrator.moderate(id, approve = true)

    @PostMapping("/{id}/reject")
    fun reject(@PathVariable id: Long): AdminReviewDto = reviewOrchestrator.moderate(id, approve = false)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = reviewOrchestrator.delete(id)
}
