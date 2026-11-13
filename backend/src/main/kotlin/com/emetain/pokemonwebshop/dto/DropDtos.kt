package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.ShipmentPreference
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

/** A drop in a list: on /drops, and the LIVE badge in the menu. */
data class DropSummaryDto(
    val slug: String,
    val name: String,
    val bannerUrl: String?,
    val startsAt: Instant,
    val endsAt: Instant?,
    val endedAt: Instant?,
    val status: String,
    val productCount: Int,
    val soldOut: Boolean,
    /** The game of all its products, for the colour. Null for a mix. */
    val game: String?,
)

data class DropListDto(
    val live: List<DropSummaryDto>,
    val upcoming: List<DropSummaryDto>,
    val past: List<DropSummaryDto>,
    val serverTime: Instant,
)

/**
 * A product on the public drop page. It has no price and no stock numbers: the shop never shows how many there are,
 * and the price only shows once it is your turn, through [DropCartDto.prices].
 */
data class DropProductDto(
    /** The id of the product in the drop. The drop cart uses this one. */
    val id: Long,
    val productId: Long,
    /** Only for a product that is in the shop, so the drop page can link to it. */
    val slug: String?,
    val name: String,
    val game: String?,
    val type: String,
    val imageUrl: String?,
    val imageIsCard: Boolean,
    val setLogoUrl: String?,
    val maxPerCustomer: Int?,
    val soldOut: Boolean,
    /** Nothing can go into a checkout now, but checkouts that aren't paid yet can still give some back. */
    val allReserved: Boolean,
    val preOrder: Boolean,
    val shipsFrom: LocalDate?,
)

data class DropDetailDto(
    val slug: String,
    val name: String,
    val description: String?,
    val bannerUrl: String?,
    val startsAt: Instant,
    val endsAt: Instant?,
    val endedAt: Instant?,
    val status: String,
    val requiresAccount: Boolean,
    val maxPerCustomer: Int?,
    val waitingRoomOpensAt: Instant,
    /** How long someone who got through has to shop and start the checkout. */
    val accessMinutes: Long,
    /** How long before the start the "herinner mij" mail goes out. */
    val reminderMinutes: Long,
    val queuePaused: Boolean,
    val soldOut: Boolean,
    val game: String?,
    val products: List<DropProductDto>,
    /** For the countdown: the browser's clock can be off. */
    val serverTime: Instant,
)

/** Every request a bot might fill in automatically has this field. People never see it, so it stays empty. */
interface HoneypotRequest {
    val website: String?
}

data class DropReminderRequest(
    /** Guests only. A logged-in customer gets the mail on the account's address. */
    @field:Size(max = 254, message = "Dit e-mailadres is te lang.")
    val email: String? = null,
    override val website: String? = null,
) : HoneypotRequest

data class JoinQueueRequest(
    override val website: String? = null,
) : HoneypotRequest

/** Where someone is in the waiting room of a drop. */
data class QueueStatusDto(
    /** `LOGIN_REQUIRED`, `NOT_OPEN`, `NOT_JOINED`, `WAITING_ROOM`, `QUEUED`, `ADMITTED`, `EXPIRED` or `CLOSED`. */
    val state: String,
    /** The place in line, counting only people who are still there. */
    val position: Long?,
    val ahead: Long?,
    val estimatedSeconds: Long?,
    /** People in the waiting room. */
    val waitingCount: Long?,
    val accessExpiresAt: Instant?,
    val cartCount: Int,
    val paused: Boolean,
    val serverTime: Instant,
    /** When to ask again. Spread out, so a big waiting room doesn't ask all at once. */
    val pollAfterMs: Long,
)

data class DropCartLimitDto(
    val dropProductId: Long,
    val maxPerCustomer: Int?,
    /** In paid orders or checkouts that hold stock. */
    val ordered: Int,
    val inCart: Int,
    /** The most this product can have in the drop cart within the limits, what is in it already included. Null: no limit. */
    val room: Int?,
)

/** The price of a product in the drop. Only for someone whose turn it is. */
data class DropPriceDto(
    val dropProductId: Long,
    /** What it costs in the drop. */
    val priceCents: Long?,
    /** The shop price, when the drop price is lower. */
    val regularPriceCents: Long?,
)

data class DropCartDto(
    val cart: CartDto,
    val limits: List<DropCartLimitDto>,
    /** Every product in the drop. The drop page has no prices, so this is where they come from. */
    val prices: List<DropPriceDto>,
    val maxPerCustomer: Int?,
    val orderedTotal: Int,
    val accessExpiresAt: Instant?,
    val reservationMinutes: Long,
    val serverTime: Instant,
)

data class DropCartQuantityRequest(
    @field:Min(0, message = "Kies een aantal van 0 of meer.")
    @field:Max(99, message = "Je kunt maximaal 99 stuks tegelijk bestellen.")
    val quantity: Int = 0,
)

/** The checkout of a drop: the same as the shop's, with the honeypot. */
data class StartDropCheckoutRequest(
    val addressId: Long? = null,
    @field:Valid
    val address: AddressRequest? = null,
    val saveAddress: Boolean = false,
    @field:Size(max = 100, message = "Je naam mag maximaal 100 tekens lang zijn.")
    val name: String? = null,
    @field:Size(max = 254, message = "Dit e-mailadres is te lang.")
    val email: String? = null,
    val shipmentPreference: ShipmentPreference? = null,
    val acceptTerms: Boolean = false,
    @field:Size(max = 60, message = "Deze kortingscode is te lang.")
    val discountCode: String? = null,
    override val website: String? = null,
) : HoneypotRequest {
    fun toCheckout() =
        StartCheckoutRequest(addressId, address, saveAddress, name, email, shipmentPreference, acceptTerms, discountCode)
}

/** A product's place in an upcoming or live drop, for its product page. Without the drop price: that only shows once it is your turn. */
data class ProductDropDto(
    val slug: String,
    val name: String,
    val status: String,
    val startsAt: Instant,
)
