package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Drop
import com.emetain.pokemonwebshop.domain.DropLeftover
import com.emetain.pokemonwebshop.domain.DropProduct
import com.emetain.pokemonwebshop.domain.DropStatus
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.DropProductRequest
import com.emetain.pokemonwebshop.dto.DropRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.DropRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * What the admin does with a drop: set it up, choose and allocate products, publish it, and steer it while it runs.
 * Prices, limits and times are fixed once the drop is live, so nobody in line sees the rules change.
 */
@Service
class DropService(
    private val dropRepository: DropRepository,
    private val dropLocks: DropLocks,
    private val productRepository: ProductRepository,
    private val dropStockService: DropStockService,
    private val mediaStorage: MediaStorage,
    private val properties: AppProperties,
    private val clock: Clock,
) {

    @Transactional
    fun create(request: DropRequest): Drop {
        val now = clock.instant()
        val drop = Drop(
            name = "",
            slug = "",
            startsAt = request.startsAt ?: throw ApiException.validation("Kies een starttijd."),
            createdAt = now,
            updatedAt = now,
            admitPerMinute = properties.drops.admitPerMinute,
        )
        apply(drop, request)
        return dropRepository.save(drop)
    }

    @Transactional
    fun update(dropId: Long, request: DropRequest): Drop {
        val drop = locked(dropId)
        when (drop.status) {
            DropStatus.DRAFT, DropStatus.SCHEDULED -> apply(drop, request)
            DropStatus.LIVE -> applyWhileLive(drop, request)
            DropStatus.ENDED -> applyAfterEnd(drop, request)
        }
        drop.updatedAt = clock.instant()
        return drop
    }

    /** Only a draft can go: nobody has seen it, and its stock goes back. */
    @Transactional
    fun delete(dropId: Long, admin: User) {
        val drop = locked(dropId)
        if (drop.status != DropStatus.DRAFT) {
            throw ApiException.validation("Alleen een concept kan weg. Haal de drop eerst offline.")
        }
        for (dropProduct in drop.products) dropStockService.setAllocation(drop, dropProduct, 0, admin)
        drop.bannerUrl?.let(mediaStorage::delete)
        dropRepository.delete(drop)
    }

    @Transactional
    fun addProduct(dropId: Long, request: DropProductRequest, admin: User): Drop {
        val drop = locked(dropId)
        requireBeforeStart(drop)
        val productId = request.productId ?: throw ApiException.validation("Kies een product.")
        if (drop.products.any { it.product.id == productId }) throw ApiException.validation("Dit product zit al in de drop.")
        if (drop.products.size >= DropRules.MAX_PRODUCTS) {
            throw ApiException.validation("Een drop kan maximaal ${DropRules.MAX_PRODUCTS} producten hebben.")
        }
        val product = productRepository.findWithDetailsById(productId) ?: throw ApiException.notFound("We konden dit product niet vinden.")
        if (product.status == ProductStatus.ARCHIVED) throw ApiException.validation("Een gearchiveerd product kan niet in een drop.")
        val dropProduct = DropProduct(
            drop = drop,
            product = product,
            position = (drop.products.maxOfOrNull { it.position } ?: 0) + 1,
        )
        applyProduct(dropProduct, request)
        drop.products += dropProduct
        dropStockService.setAllocation(drop, dropProduct, request.allocated, admin)
        drop.updatedAt = clock.instant()
        return drop
    }

    /** Prices and limits until the start. The allocation can change until the end, never below what is sold or held. */
    @Transactional
    fun updateProduct(dropId: Long, dropProductId: Long, request: DropProductRequest, admin: User): Drop {
        val drop = locked(dropId)
        if (drop.status == DropStatus.ENDED) throw ApiException.validation("Deze drop is afgelopen.")
        val dropProduct = productOf(drop, dropProductId)
        if (drop.status == DropStatus.LIVE) {
            if (request.priceCents != dropProduct.priceCents || request.maxPerCustomer != dropProduct.maxPerCustomer) {
                throw ApiException.validation("Tijdens de drop kun je prijzen en limieten niet meer wijzigen, alleen de toewijzing.")
            }
        } else {
            applyProduct(dropProduct, request)
        }
        dropStockService.setAllocation(drop, dropProduct, request.allocated, admin)
        drop.updatedAt = clock.instant()
        return drop
    }

    @Transactional
    fun removeProduct(dropId: Long, dropProductId: Long, admin: User): Drop {
        val drop = locked(dropId)
        requireBeforeStart(drop)
        val dropProduct = productOf(drop, dropProductId)
        dropStockService.setAllocation(drop, dropProduct, 0, admin)
        drop.products.remove(dropProduct)
        drop.updatedAt = clock.instant()
        return drop
    }

    /** A draft goes public: on /drops, and in the hero when that is on. */
    @Transactional
    fun publish(dropId: Long): Drop {
        val drop = locked(dropId)
        if (drop.status != DropStatus.DRAFT) throw ApiException.validation("Deze drop is al gepubliceerd.")
        if (drop.products.isEmpty()) throw ApiException.validation("Voeg eerst producten toe.")
        drop.products.firstOrNull { it.allocated <= 0 }?.let {
            throw ApiException.validation("Wijs voorraad toe aan ${it.product.name}, of haal het uit de drop.")
        }
        drop.products.firstOrNull { (it.priceCents ?: ProductRules.shownPriceCents(it.product)) == null }?.let {
            throw ApiException.validation("${it.product.name} heeft geen prijs. Geef het een dropprijs of een prijs in de winkel.")
        }
        if (!drop.startsAt.isAfter(clock.instant())) throw ApiException.validation("Kies een starttijd in de toekomst.")
        drop.status = DropStatus.SCHEDULED
        drop.updatedAt = clock.instant()
        return drop
    }

    /** Back to a draft, until the start. People who asked for a reminder keep their signup. */
    @Transactional
    fun unpublish(dropId: Long): Drop {
        val drop = locked(dropId)
        if (drop.status != DropStatus.SCHEDULED) throw ApiException.validation("Alleen een geplande drop kan terug naar concept.")
        if (!drop.startsAt.isAfter(clock.instant())) throw ApiException.validation("Deze drop begint al.")
        drop.status = DropStatus.DRAFT
        drop.updatedAt = clock.instant()
        return drop
    }

    /** Pauses or resumes the waiting room, or changes its speed. */
    @Transactional
    fun steerQueue(dropId: Long, paused: Boolean?, admitPerMinute: Int?): Drop {
        val drop = locked(dropId)
        if (drop.status == DropStatus.ENDED) throw ApiException.validation("Deze drop is afgelopen.")
        admitPerMinute?.let {
            requireAdmitRate(it)
            drop.admitPerMinute = it
        }
        paused?.let { drop.queuePaused = it }
        drop.updatedAt = clock.instant()
        return drop
    }

    /** Ends a live drop now, by the admin. */
    @Transactional
    fun endEarly(dropId: Long, admin: User): Drop {
        val drop = locked(dropId)
        if (drop.status != DropStatus.LIVE) throw ApiException.validation("Alleen een live drop kan vroeg stoppen.")
        end(drop, "Vroeg beëindigd door ${admin.displayName}", admin)
        return drop
    }

    /** For a drop that kept its leftover: gives it back to the normal stock now. */
    @Transactional
    fun returnLeftover(dropId: Long, admin: User): Drop {
        val drop = locked(dropId)
        if (drop.status != DropStatus.ENDED) throw ApiException.validation("Geef de voorraad terug nadat de drop is afgelopen.")
        if (drop.leftoverReturnedAt != null) throw ApiException.validation("De voorraad van deze drop is al teruggezet.")
        dropStockService.returnLeftover(drop, admin)
        drop.updatedAt = clock.instant()
        return drop
    }

    /**
     * Ends the drop. Call this with the drop locked. Unsold stock goes back now when the drop is set up that way.
     * Checkouts that still hold stock can be paid: what they hold follows when they're released.
     */
    fun end(drop: Drop, reason: String, admin: User? = null) {
        if (drop.status == DropStatus.ENDED) return
        val now = clock.instant()
        drop.status = DropStatus.ENDED
        drop.endedAt = now
        drop.endReason = reason.take(REASON_LENGTH)
        drop.updatedAt = now
        if (drop.leftover == DropLeftover.RETURN_TO_STOCK) dropStockService.returnLeftover(drop, admin)
    }

    @Transactional
    fun replaceBanner(dropId: Long, bytes: ByteArray): Drop {
        val drop = locked(dropId)
        val format = ImageFormat.detect(bytes) ?: throw ApiException.validation("Kies een afbeelding in JPG, PNG of WebP.")
        val previous = drop.bannerUrl
        drop.bannerUrl = mediaStorage.store(bytes, format)
        previous?.let(mediaStorage::delete)
        drop.updatedAt = clock.instant()
        return drop
    }

    @Transactional
    fun removeBanner(dropId: Long): Drop {
        val drop = locked(dropId)
        drop.bannerUrl?.let(mediaStorage::delete)
        drop.bannerUrl = null
        drop.updatedAt = clock.instant()
        return drop
    }

    private fun apply(drop: Drop, request: DropRequest) {
        val startsAt = request.startsAt ?: throw ApiException.validation("Kies een starttijd.")
        if (request.endsAt != null && !request.endsAt.isAfter(startsAt)) {
            throw ApiException.validation("De eindtijd moet na de starttijd liggen.")
        }
        if (drop.status == DropStatus.SCHEDULED && !startsAt.isAfter(clock.instant())) {
            throw ApiException.validation("Kies een starttijd in de toekomst.")
        }
        request.maxPerCustomer?.let { requireLimit(it) }
        request.admitPerMinute?.let { requireAdmitRate(it) }
        drop.name = request.name.trim()
        drop.slug = slug(drop, request)
        drop.description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        drop.startsAt = startsAt
        drop.endsAt = request.endsAt
        drop.requiresAccount = request.requiresAccount
        drop.maxPerCustomer = request.maxPerCustomer
        drop.leftover = leftover(request.leftover)
        drop.showInHero = request.showInHero
        request.admitPerMinute?.let { drop.admitPerMinute = it }
    }

    /** During the drop: the text, the end time, the hero, the speed and what happens to the leftover. */
    private fun applyWhileLive(drop: Drop, request: DropRequest) {
        val fixed = request.startsAt != drop.startsAt ||
            request.requiresAccount != drop.requiresAccount ||
            request.maxPerCustomer != drop.maxPerCustomer ||
            (request.slug != null && request.slug != drop.slug)
        if (fixed) {
            throw ApiException.validation("Tijdens de drop kun je de starttijd, het adres, de limiet en de accountplicht niet meer wijzigen.")
        }
        if (request.endsAt != null && !request.endsAt.isAfter(clock.instant())) {
            throw ApiException.validation("Kies een eindtijd in de toekomst, of beëindig de drop met de knop.")
        }
        request.admitPerMinute?.let {
            requireAdmitRate(it)
            drop.admitPerMinute = it
        }
        drop.name = request.name.trim()
        drop.description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        drop.endsAt = request.endsAt
        drop.showInHero = request.showInHero
        drop.leftover = leftover(request.leftover)
    }

    /** After the drop only the text can change: the report and the orders keep pointing at it. */
    private fun applyAfterEnd(drop: Drop, request: DropRequest) {
        drop.name = request.name.trim()
        drop.description = request.description?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun applyProduct(dropProduct: DropProduct, request: DropProductRequest) {
        request.maxPerCustomer?.let { requireLimit(it) }
        if (request.priceCents != null && request.priceCents < 0) throw ApiException.validation("Een prijs kan niet negatief zijn.")
        dropProduct.maxPerCustomer = request.maxPerCustomer
        dropProduct.priceCents = request.priceCents
    }

    private fun slug(drop: Drop, request: DropRequest): String {
        val wanted = request.slug?.trim()?.takeIf { it.isNotEmpty() }
        if (wanted == null) {
            if (drop.slug.isNotEmpty()) return drop.slug
            return ProductSlugs.unique(request.name) { it in RESERVED_SLUGS || dropRepository.existsBySlug(it) }
        }
        if (!ProductSlugs.isValid(wanted) || wanted in RESERVED_SLUGS) {
            throw ApiException.validation("Een webadres heeft alleen kleine letters, cijfers en streepjes.")
        }
        if (wanted != drop.slug && dropRepository.existsBySlug(wanted)) {
            throw ApiException(HttpStatus.CONFLICT, "slug_taken", "Een andere drop heeft dit webadres al.")
        }
        return wanted
    }

    private fun leftover(value: String?): DropLeftover =
        value?.let { runCatching { DropLeftover.valueOf(it) }.getOrNull() } ?: DropLeftover.RETURN_TO_STOCK

    private fun requireBeforeStart(drop: Drop) {
        if (drop.status == DropStatus.LIVE || drop.status == DropStatus.ENDED) {
            throw ApiException.validation("Na de start kun je geen producten meer toevoegen of weghalen.")
        }
    }

    private fun requireLimit(limit: Int) {
        if (limit !in 1..CartRules.MAX_PER_LINE) throw ApiException.validation("Kies een limiet van 1 tot ${CartRules.MAX_PER_LINE}.")
    }

    private fun requireAdmitRate(perMinute: Int) {
        if (perMinute !in 1..MAX_ADMIT_PER_MINUTE) {
            throw ApiException.validation("Kies een snelheid van 1 tot $MAX_ADMIT_PER_MINUTE mensen per minuut.")
        }
    }

    private fun productOf(drop: Drop, dropProductId: Long): DropProduct =
        drop.products.firstOrNull { it.id == dropProductId } ?: throw ApiException.notFound("Dit product zit niet in de drop.")

    private fun locked(dropId: Long): Drop =
        dropLocks.lock(dropId) ?: throw ApiException.notFound("We konden deze drop niet vinden.")

    private companion object {
        const val MAX_ADMIT_PER_MINUTE = 10_000
        const val REASON_LENGTH = 200

        /** Paths next to `/drops/{slug}` in the API. */
        val RESERVED_SLUGS = setOf("live")
    }
}
