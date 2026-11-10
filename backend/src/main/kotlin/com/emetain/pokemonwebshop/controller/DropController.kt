package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CheckoutStartDto
import com.emetain.pokemonwebshop.dto.DropCartDto
import com.emetain.pokemonwebshop.dto.DropCartQuantityRequest
import com.emetain.pokemonwebshop.dto.DropDetailDto
import com.emetain.pokemonwebshop.dto.DropListDto
import com.emetain.pokemonwebshop.dto.DropReminderRequest
import com.emetain.pokemonwebshop.dto.DropSummaryDto
import com.emetain.pokemonwebshop.dto.JoinQueueRequest
import com.emetain.pokemonwebshop.dto.QueueStatusDto
import com.emetain.pokemonwebshop.dto.StartDropCheckoutRequest
import com.emetain.pokemonwebshop.orchestration.DropOrchestrator
import com.emetain.pokemonwebshop.orchestration.DropShopOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RateLimited
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Drops for customers. Every endpoint that changes something is rate limited per IP address, and per account where
 * many people can share one address. The cart and checkout need the access token of the waiting room.
 */
@RestController
@RequestMapping("/v1/drops")
class DropController(
    private val dropOrchestrator: DropOrchestrator,
    private val dropShopOrchestrator: DropShopOrchestrator,
) {

    @GetMapping
    fun list(): DropListDto = dropOrchestrator.list()

    /** Live drops, for the LIVE badge in the menu. */
    @GetMapping("/live")
    fun live(): List<DropSummaryDto> = dropOrchestrator.live()

    @GetMapping("/{slug}")
    fun detail(@PathVariable slug: String, @CurrentUser viewer: User?): DropDetailDto = dropOrchestrator.detail(slug, viewer)

    @PostMapping("/{slug}/reminders")
    @RateLimited("drop-reminder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remind(@PathVariable slug: String, @CurrentUser viewer: User?, @Valid @RequestBody body: DropReminderRequest) =
        dropOrchestrator.remind(slug, viewer, body)

    /** Joins the waiting room, or gets back in line after the time ran out. */
    @PostMapping("/{slug}/queue")
    @RateLimited("drop-join")
    fun join(
        @PathVariable slug: String,
        @CurrentUser viewer: User?,
        request: HttpServletRequest,
        @RequestBody(required = false) body: JoinQueueRequest?,
    ): ResponseEntity<QueueStatusDto> = dropOrchestrator.join(slug, viewer, request, body ?: JoinQueueRequest())

    /** Where am I in line? Hands out the access token once it's this person's turn. */
    @PostMapping("/{slug}/queue/ping")
    @RateLimited("drop-ping")
    fun ping(@PathVariable slug: String, @CurrentUser viewer: User?, request: HttpServletRequest): ResponseEntity<QueueStatusDto> =
        dropOrchestrator.ping(slug, viewer, request)

    @GetMapping("/{slug}/cart")
    @RateLimited("drop-cart")
    fun cart(
        @PathVariable slug: String,
        @CurrentUser viewer: User?,
        request: HttpServletRequest,
        @RequestParam(required = false) country: String?,
    ): DropCartDto = dropShopOrchestrator.cart(slug, viewer, request, country)

    @PutMapping("/{slug}/cart/{dropProductId}")
    @RateLimited("drop-cart")
    fun setQuantity(
        @PathVariable slug: String,
        @PathVariable dropProductId: Long,
        @CurrentUser viewer: User?,
        request: HttpServletRequest,
        @RequestParam(required = false) country: String?,
        @Valid @RequestBody body: DropCartQuantityRequest,
    ): DropCartDto = dropShopOrchestrator.setQuantity(slug, viewer, request, dropProductId, body.quantity, country)

    @PostMapping("/{slug}/checkout")
    @RateLimited("drop-checkout")
    @ResponseStatus(HttpStatus.CREATED)
    fun checkout(
        @PathVariable slug: String,
        @CurrentUser viewer: User?,
        request: HttpServletRequest,
        @Valid @RequestBody body: StartDropCheckoutRequest,
    ): CheckoutStartDto = dropShopOrchestrator.checkout(slug, viewer, request, body)
}
