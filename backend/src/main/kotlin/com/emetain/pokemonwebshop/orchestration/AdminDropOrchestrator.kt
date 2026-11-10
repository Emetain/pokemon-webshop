package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Drop
import com.emetain.pokemonwebshop.domain.DropProduct
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminDropDto
import com.emetain.pokemonwebshop.dto.AdminDropProductDto
import com.emetain.pokemonwebshop.dto.AdminDropRowDto
import com.emetain.pokemonwebshop.dto.DropLiveDto
import com.emetain.pokemonwebshop.dto.DropProductRequest
import com.emetain.pokemonwebshop.dto.DropProductStatsDto
import com.emetain.pokemonwebshop.dto.DropQueueRequest
import com.emetain.pokemonwebshop.dto.DropQueueStatsDto
import com.emetain.pokemonwebshop.dto.DropRequest
import com.emetain.pokemonwebshop.dto.DropTotalsDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.DropQueueEntryRepository
import com.emetain.pokemonwebshop.repository.DropReminderRepository
import com.emetain.pokemonwebshop.repository.DropRepository
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.emetain.pokemonwebshop.service.DropService
import com.emetain.pokemonwebshop.service.ProductRules
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration

@Component
class AdminDropOrchestrator(
    private val dropService: DropService,
    private val dropRepository: DropRepository,
    private val entryRepository: DropQueueEntryRepository,
    private val reminderRepository: DropReminderRepository,
    private val orderRepository: OrderRepository,
    private val properties: AppProperties,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock,
) {
    private val readOnly = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    @Transactional(readOnly = true)
    fun list(): List<AdminDropRowDto> = dropRepository.findAllNewestFirst().map { drop ->
        AdminDropRowDto(
            id = drop.id,
            name = drop.name,
            slug = drop.slug,
            status = drop.status.name,
            startsAt = drop.startsAt,
            endsAt = drop.endsAt,
            endedAt = drop.endedAt,
            bannerUrl = drop.bannerUrl,
            productCount = drop.products.size,
            allocated = drop.products.sumOf { it.allocated },
            sold = drop.products.sumOf { it.sold },
        )
    }

    @Transactional(readOnly = true)
    fun get(dropId: Long): AdminDropDto = dto(find(dropId))

    @Transactional
    fun create(request: DropRequest): AdminDropDto = dto(dropService.create(request))

    @Transactional
    fun update(dropId: Long, request: DropRequest): AdminDropDto = dto(dropService.update(dropId, request))

    fun delete(dropId: Long, admin: User) = retryingOnStockConflict { dropService.delete(dropId, admin) }

    @Transactional
    fun publish(dropId: Long): AdminDropDto = dto(dropService.publish(dropId))

    @Transactional
    fun unpublish(dropId: Long): AdminDropDto = dto(dropService.unpublish(dropId))

    fun addProduct(dropId: Long, request: DropProductRequest, admin: User): AdminDropDto {
        retryingOnStockConflict { dropService.addProduct(dropId, request, admin) }
        return read(dropId)
    }

    fun updateProduct(dropId: Long, dropProductId: Long, request: DropProductRequest, admin: User): AdminDropDto {
        retryingOnStockConflict { dropService.updateProduct(dropId, dropProductId, request, admin) }
        return read(dropId)
    }

    fun removeProduct(dropId: Long, dropProductId: Long, admin: User): AdminDropDto {
        retryingOnStockConflict { dropService.removeProduct(dropId, dropProductId, admin) }
        return read(dropId)
    }

    @Transactional
    fun replaceBanner(dropId: Long, bytes: ByteArray): AdminDropDto = dto(dropService.replaceBanner(dropId, bytes))

    @Transactional
    fun removeBanner(dropId: Long): AdminDropDto = dto(dropService.removeBanner(dropId))

    fun steerQueue(dropId: Long, request: DropQueueRequest): DropLiveDto {
        dropService.steerQueue(dropId, request.paused, request.admitPerMinute)
        return live(dropId)
    }

    fun endEarly(dropId: Long, admin: User): DropLiveDto {
        retryingOnStockConflict { dropService.endEarly(dropId, admin) }
        return live(dropId)
    }

    fun returnLeftover(dropId: Long, admin: User): DropLiveDto {
        retryingOnStockConflict { dropService.returnLeftover(dropId, admin) }
        return live(dropId)
    }

    /** The live view during the drop and the report afterwards. Its own transaction, also when called from here. */
    fun live(dropId: Long): DropLiveDto = readOnly.execute { liveDto(dropId) }!!

    /** A fresh read after a change made in its own transaction. Calls within this class skip `@Transactional`. */
    private fun read(dropId: Long): AdminDropDto = readOnly.execute { dto(find(dropId)) }!!

    private fun liveDto(dropId: Long): DropLiveDto {
        val drop = find(dropId)
        val now = clock.instant()
        val seenSince = now.minus(properties.drops.activeWindow)
        val sales = orderRepository.dropSales(drop.id).associateBy { it.productId }
        val start = drop.startedAt ?: drop.startsAt
        val products = drop.products.map { dropProduct ->
            DropProductStatsDto(
                id = dropProduct.id,
                productId = dropProduct.product.id,
                name = dropProduct.product.name,
                allocated = dropProduct.allocated,
                reserved = dropProduct.reserved,
                sold = dropProduct.sold,
                available = dropProduct.available.coerceAtLeast(0),
                revenueCents = sales[dropProduct.product.id]?.revenueCents ?: 0,
                soldOutAt = dropProduct.soldOutAt,
                sellOutSeconds = dropProduct.soldOutAt?.let { Duration.between(start, it).seconds.coerceAtLeast(0) },
            )
        }
        val sellOuts = products.mapNotNull { it.sellOutSeconds }
        return DropLiveDto(
            id = drop.id,
            name = drop.name,
            slug = drop.slug,
            status = drop.status.name,
            startsAt = drop.startsAt,
            startedAt = drop.startedAt,
            endsAt = drop.endsAt,
            endedAt = drop.endedAt,
            endReason = drop.endReason,
            leftover = drop.leftover.name,
            leftoverReturnedAt = drop.leftoverReturnedAt,
            queue = DropQueueStatsDto(
                joined = entryRepository.countByDropId(drop.id),
                waiting = entryRepository.countWaiting(drop.id, drop.admittedUpTo),
                activeWaiting = entryRepository.countActiveWaiting(drop.id, drop.admittedUpTo, seenSince),
                admitted = entryRepository.countByDropIdAndAdmittedAtIsNotNull(drop.id),
                shopping = entryRepository.countByDropIdAndAccessExpiresAtAfter(drop.id, now),
                shuffled = drop.shuffledCount,
                admitPerMinute = drop.admitPerMinute,
                paused = drop.queuePaused,
            ),
            products = products,
            totals = DropTotalsDto(
                orders = orderRepository.countDropOrders(drop.id),
                buyers = orderRepository.countDropBuyers(drop.id),
                units = products.sumOf { it.sold },
                revenueCents = products.sumOf { it.revenueCents },
                averageSellOutSeconds = if (sellOuts.isEmpty()) null else sellOuts.sum() / sellOuts.size,
                cancelledOrders = orderRepository.countDropPaidOrdersWithStatus(drop.id, listOf(OrderStatus.CANCELLED, OrderStatus.REFUNDED)),
                reminders = reminderRepository.countByDropId(drop.id),
            ),
            serverTime = now,
        )
    }

    private fun dto(drop: Drop) = AdminDropDto(
        id = drop.id,
        name = drop.name,
        slug = drop.slug,
        description = drop.description,
        bannerUrl = drop.bannerUrl,
        startsAt = drop.startsAt,
        endsAt = drop.endsAt,
        status = drop.status.name,
        requiresAccount = drop.requiresAccount,
        maxPerCustomer = drop.maxPerCustomer,
        leftover = drop.leftover.name,
        showInHero = drop.showInHero,
        admitPerMinute = drop.admitPerMinute,
        queuePaused = drop.queuePaused,
        startedAt = drop.startedAt,
        endedAt = drop.endedAt,
        endReason = drop.endReason,
        leftoverReturnedAt = drop.leftoverReturnedAt,
        products = drop.products.map(::product),
        reminderCount = if (drop.id == 0L) 0 else reminderRepository.countByDropId(drop.id),
    )

    private fun product(dropProduct: DropProduct): AdminDropProductDto {
        val product = dropProduct.product
        val ownImage = product.images.firstOrNull()
        val cardImage = product.card?.shownImageSmallUrl
        return AdminDropProductDto(
            id = dropProduct.id,
            productId = product.id,
            name = product.name,
            slug = product.slug,
            type = product.type.name,
            game = product.game?.slug,
            imageUrl = ownImage ?: cardImage,
            imageIsCard = ownImage == null && cardImage != null,
            setLogoUrl = product.cardSet?.shownLogoUrl,
            productStatus = product.status.name,
            shopPriceCents = ProductRules.shownPriceCents(product),
            priceCents = dropProduct.priceCents,
            maxPerCustomer = dropProduct.maxPerCustomer,
            allocated = dropProduct.allocated,
            reserved = dropProduct.reserved,
            sold = dropProduct.sold,
            available = dropProduct.available.coerceAtLeast(0),
            freeStock = product.stock.available.coerceAtLeast(0),
            soldOutAt = dropProduct.soldOutAt,
        )
    }

    private fun find(dropId: Long): Drop =
        dropRepository.findById(dropId).orElseThrow { ApiException.notFound("We konden deze drop niet vinden.") }
}
