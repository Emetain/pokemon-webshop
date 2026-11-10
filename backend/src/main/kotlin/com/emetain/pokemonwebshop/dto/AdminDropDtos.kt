package com.emetain.pokemonwebshop.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class DropRequest(
    @field:NotBlank(message = "Vul een naam in.")
    @field:Size(max = 120, message = "De naam mag maximaal 120 tekens hebben.")
    val name: String = "",
    /** Made from the name when empty. */
    @field:Size(max = 140, message = "Het webadres mag maximaal 140 tekens hebben.")
    val slug: String? = null,
    @field:Size(max = 5000, message = "De beschrijving mag maximaal 5000 tekens hebben.")
    val description: String? = null,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
    val requiresAccount: Boolean = true,
    val maxPerCustomer: Int? = null,
    /** `RETURN_TO_STOCK` or `KEEP`. */
    val leftover: String? = null,
    val showInHero: Boolean = true,
    val admitPerMinute: Int? = null,
)

data class DropProductRequest(
    /** Only when adding a product. */
    val productId: Long? = null,
    @field:Min(0, message = "Een toewijzing kan niet negatief zijn.")
    val allocated: Int = 0,
    val maxPerCustomer: Int? = null,
    /** Null: the shop price. */
    val priceCents: Long? = null,
)

data class DropQueueRequest(
    val paused: Boolean? = null,
    val admitPerMinute: Int? = null,
)

data class AdminDropProductDto(
    val id: Long,
    val productId: Long,
    val name: String,
    val slug: String,
    val type: String,
    val game: String?,
    val imageUrl: String?,
    val imageIsCard: Boolean,
    val setLogoUrl: String?,
    val productStatus: String,
    /** What the product costs in the shop, sale price included. */
    val shopPriceCents: Long?,
    val priceCents: Long?,
    val maxPerCustomer: Int?,
    val allocated: Int,
    val reserved: Int,
    val sold: Int,
    val available: Int,
    /** What is still free in the normal stock, so more can be allocated. */
    val freeStock: Int,
    val soldOutAt: Instant?,
)

data class AdminDropDto(
    val id: Long,
    val name: String,
    val slug: String,
    val description: String?,
    val bannerUrl: String?,
    val startsAt: Instant,
    val endsAt: Instant?,
    val status: String,
    val requiresAccount: Boolean,
    val maxPerCustomer: Int?,
    val leftover: String,
    val showInHero: Boolean,
    val admitPerMinute: Int,
    val queuePaused: Boolean,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val endReason: String?,
    val leftoverReturnedAt: Instant?,
    val products: List<AdminDropProductDto>,
    val reminderCount: Long,
)

data class AdminDropRowDto(
    val id: Long,
    val name: String,
    val slug: String,
    val status: String,
    val startsAt: Instant,
    val endsAt: Instant?,
    val endedAt: Instant?,
    val bannerUrl: String?,
    val productCount: Int,
    val allocated: Int,
    val sold: Int,
)

data class DropQueueStatsDto(
    /** Everyone who ever joined. */
    val joined: Long,
    /** Not let through yet. */
    val waiting: Long,
    /** Not let through yet and still on the page. */
    val activeWaiting: Long,
    val admitted: Long,
    /** Let through, and their time hasn't run out. */
    val shopping: Long,
    val shuffled: Int,
    val admitPerMinute: Int,
    val paused: Boolean,
)

data class DropProductStatsDto(
    val id: Long,
    val productId: Long,
    val name: String,
    val allocated: Int,
    val reserved: Int,
    val sold: Int,
    val available: Int,
    val revenueCents: Long,
    val soldOutAt: Instant?,
    /** From the start to the last one sold. */
    val sellOutSeconds: Long?,
)

data class DropTotalsDto(
    val orders: Long,
    val buyers: Long,
    val units: Int,
    val revenueCents: Long,
    /** Of the products that sold out. */
    val averageSellOutSeconds: Long?,
    val cancelledOrders: Long,
    val reminders: Long,
)

/** The live view during a drop, and the report afterwards: the same numbers. */
data class DropLiveDto(
    val id: Long,
    val name: String,
    val slug: String,
    val status: String,
    val startsAt: Instant,
    val startedAt: Instant?,
    val endsAt: Instant?,
    val endedAt: Instant?,
    val endReason: String?,
    val leftover: String,
    val leftoverReturnedAt: Instant?,
    val queue: DropQueueStatsDto,
    val products: List<DropProductStatsDto>,
    val totals: DropTotalsDto,
    val serverTime: Instant,
)
