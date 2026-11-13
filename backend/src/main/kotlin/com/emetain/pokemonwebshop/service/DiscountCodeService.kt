package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.DiscountCode
import com.emetain.pokemonwebshop.domain.DiscountKind
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.DiscountCodeRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.DiscountCodeRepository
import com.emetain.pokemonwebshop.repository.OrderRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Locale

/** A code that applies, with what it takes off. */
data class AppliedDiscount(val code: DiscountCode, val amountCents: Long)

/** What the cart and checkout page show for a code: the amount, or why it doesn't apply. */
data class DiscountQuote(val code: String, val amountCents: Long, val problem: String?)

/** Usage of one code: orders that aren't cancelled, of which paid, and the discount given on the paid ones. */
data class DiscountUsage(val uses: Long, val paidUses: Long, val givenCents: Long)

@Service
class DiscountCodeService(
    private val codeRepository: DiscountCodeRepository,
    private val orderRepository: OrderRepository,
    private val clock: Clock,
) {

    /**
     * For the cart and checkout page. Never fails: a code that doesn't apply comes back with a message. A guest's
     * "once per customer" can only be checked at checkout, when the email address is known.
     */
    @Transactional(readOnly = true)
    fun quote(rawCode: String, lines: List<DiscountLine>, viewer: User?, drop: Boolean): DiscountQuote {
        val normalized = DiscountRules.normalize(rawCode)
        val code = codeRepository.findByCode(normalized)
        val problem = problem(code, lines, viewer?.id, viewer?.email, drop)
        return if (problem == null) {
            DiscountQuote(code!!.code, DiscountRules.discountCents(DiscountTerms.of(code), lines), null)
        } else {
            DiscountQuote(normalized, 0, DiscountRules.message(problem, code?.let(DiscountTerms::of)))
        }
    }

    /**
     * At checkout, in the transaction that makes the order. Locks the code until that transaction ends, so every
     * other checkout with it waits and then counts this order too: a code with one use left can't be used twice.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun applyLocked(rawCode: String, lines: List<DiscountLine>, viewer: User?, email: String, drop: Boolean): AppliedDiscount {
        val code = codeRepository.findLockedByCode(DiscountRules.normalize(rawCode))
        problem(code, lines, viewer?.id, email, drop)?.let {
            throw ApiException(HttpStatus.CONFLICT, "discount_invalid", DiscountRules.message(it, code?.let(DiscountTerms::of)))
        }
        return AppliedDiscount(code!!, DiscountRules.discountCents(DiscountTerms.of(code), lines))
    }

    private fun problem(code: DiscountCode?, lines: List<DiscountLine>, userId: Long?, email: String?, drop: Boolean): DiscountProblem? {
        if (code == null) return DiscountProblem.UNKNOWN
        DiscountRules.timeProblem(code.active, code.startsAt, code.endsAt, clock.instant())?.let { return it }
        val uses = if (code.maxUses != null) orderRepository.countDiscountUses(code.id) else 0
        val usesByCustomer = if (code.oncePerCustomer && (userId != null || email != null)) {
            orderRepository.countDiscountUsesBy(code.id, userId ?: NO_USER, email?.lowercase(Locale.ROOT).orEmpty())
        } else {
            0
        }
        DiscountRules.usageProblem(code.maxUses, uses, code.oncePerCustomer, usesByCustomer)?.let { return it }
        return DiscountRules.orderProblem(DiscountTerms.of(code), lines, drop)
    }

    @Transactional(readOnly = true)
    fun all(): List<DiscountCode> = codeRepository.findAllByOrderByCreatedAtDesc()

    @Transactional(readOnly = true)
    fun usage(): Map<Long, DiscountUsage> = orderRepository.discountUsage().associate { row ->
        (row[0] as Number).toLong() to DiscountUsage(
            uses = (row[1] as Number).toLong(),
            paidUses = (row[2] as Number?)?.toLong() ?: 0,
            givenCents = (row[3] as Number?)?.toLong() ?: 0,
        )
    }

    @Transactional
    fun create(request: DiscountCodeRequest): DiscountCode {
        val now = clock.instant()
        val code = DiscountCode(code = "", kind = DiscountKind.PERCENTAGE, createdAt = now, updatedAt = now)
        apply(code, request)
        return codeRepository.save(code)
    }

    @Transactional
    fun update(codeId: Long, request: DiscountCodeRequest): DiscountCode {
        val code = find(codeId)
        val used = orderRepository.existsByDiscountCodeId(codeId)
        if (used && DiscountRules.normalize(request.code) != code.code) {
            throw ApiException.validation("Deze code is al gebruikt in een bestelling. Maak een nieuwe code in plaats van deze te hernoemen.")
        }
        apply(code, request)
        code.updatedAt = clock.instant()
        return code
    }

    /** A code that was used stays, so its orders keep pointing at it: switch it off instead. */
    @Transactional
    fun delete(codeId: Long) {
        val code = find(codeId)
        if (orderRepository.existsByDiscountCodeId(codeId)) {
            throw ApiException.validation("Deze code is al gebruikt in een bestelling. Zet hem uit in plaats van hem te verwijderen.")
        }
        codeRepository.delete(code)
    }

    private fun apply(code: DiscountCode, request: DiscountCodeRequest) {
        val normalized = DiscountRules.normalize(request.code)
        if (!DiscountRules.isValidCode(normalized)) {
            throw ApiException.validation("Een code heeft 3 tot 40 letters, cijfers, - of _, zonder spaties.")
        }
        if (normalized != code.code && codeRepository.existsByCode(normalized)) {
            throw ApiException(HttpStatus.CONFLICT, "code_taken", "Deze code bestaat al.")
        }
        val kind = when (request.kind) {
            "PERCENTAGE" -> DiscountKind.PERCENTAGE
            "FIXED_AMOUNT" -> DiscountKind.FIXED_AMOUNT
            else -> throw ApiException.validation("Kies een percentage of een vast bedrag.")
        }
        when (kind) {
            DiscountKind.PERCENTAGE -> if (request.percent == null || request.percent !in 1..100) {
                throw ApiException.validation("Kies een percentage van 1 tot 100.")
            }
            DiscountKind.FIXED_AMOUNT -> if (request.amountCents == null || request.amountCents <= 0) {
                throw ApiException.validation("Vul een bedrag in van meer dan € 0.")
            }
        }
        if (request.minimumOrderCents != null && request.minimumOrderCents < 0) {
            throw ApiException.validation("Het minimale bedrag kan niet negatief zijn.")
        }
        if (request.maxUses != null && request.maxUses < 1) throw ApiException.validation("Kies minstens 1 keer, of laat het leeg.")
        if (request.startsAt != null && request.endsAt != null && !request.endsAt.isAfter(request.startsAt)) {
            throw ApiException.validation("De einddatum moet na de startdatum liggen.")
        }
        val games = request.games.map { Game.fromSlug(it) ?: throw ApiException.validation("Kies een geldig spel.") }
        val types = request.productTypes.map {
            runCatching { ProductType.valueOf(it) }.getOrElse { throw ApiException.validation("Kies een geldig soort product.") }
        }
        code.code = normalized
        code.kind = kind
        code.percent = request.percent.takeIf { kind == DiscountKind.PERCENTAGE }
        code.amountCents = request.amountCents.takeIf { kind == DiscountKind.FIXED_AMOUNT }
        code.minimumOrderCents = request.minimumOrderCents?.takeIf { it > 0 }
        code.startsAt = request.startsAt
        code.endsAt = request.endsAt
        code.maxUses = request.maxUses
        code.oncePerCustomer = request.oncePerCustomer
        code.appliesToDrops = request.appliesToDrops
        code.active = request.active
        code.note = request.note?.trim()?.takeIf { it.isNotEmpty() }
        code.games.clear()
        code.games.addAll(games)
        code.productTypes.clear()
        code.productTypes.addAll(types)
    }

    private fun find(codeId: Long): DiscountCode =
        codeRepository.findById(codeId).orElseThrow { ApiException.notFound("We konden deze kortingscode niet vinden.") }

    private companion object {
        /** No account has this id, so a guest only matches on email. */
        const val NO_USER = -1L
    }
}
