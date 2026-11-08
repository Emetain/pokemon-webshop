package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.StockMovement
import com.emetain.pokemonwebshop.domain.StockReason
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page
import java.time.Instant

data class StockDto(
    val onHand: Int,
    val reserved: Int,
    val available: Int,
    val lowStockThreshold: Int,
    val low: Boolean,
) {
    companion object {
        fun from(product: Product) = StockDto(
            onHand = product.stock.onHand,
            reserved = product.stock.reserved,
            available = product.stock.available,
            lowStockThreshold = product.lowStockThreshold,
            low = product.stock.available <= product.lowStockThreshold,
        )
    }
}

/** [change] is the change to the quantity on hand. */
data class StockAdjustRequest(
    val change: Int = 0,
    val reason: StockReason = StockReason.PURCHASE,
    @field:Size(max = 500, message = "De notitie mag maximaal 500 tekens hebben.")
    val note: String? = null,
)

/** Sets the counted quantity on hand, written as a correction. */
data class StockCountRequest(
    val onHand: Int = 0,
    @field:Size(max = 500, message = "De notitie mag maximaal 500 tekens hebben.")
    val note: String? = null,
)

data class StockMovementDto(
    val id: Long,
    val productId: Long,
    val productName: String?,
    val productSlug: String?,
    val change: Int,
    val reason: StockReason,
    val onHandAfter: Int,
    val reservedAfter: Int,
    val note: String?,
    val adminName: String?,
    val createdAt: Instant,
) {
    companion object {
        /** [withProduct] adds the product name, for lists across products. */
        fun from(movement: StockMovement, withProduct: Boolean = false) = StockMovementDto(
            id = movement.id,
            productId = movement.product.id,
            productName = if (withProduct) movement.product.name else null,
            productSlug = if (withProduct) movement.product.slug else null,
            change = movement.change,
            reason = movement.reason,
            onHandAfter = movement.onHandAfter,
            reservedAfter = movement.reservedAfter,
            note = movement.note,
            adminName = movement.adminUser?.displayName,
            createdAt = movement.createdAt,
        )
    }
}

data class StockMovementPageDto(
    val items: List<StockMovementDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
) {
    companion object {
        fun from(movements: Page<StockMovement>, withProduct: Boolean = false) = StockMovementPageDto(
            items = movements.content.map { StockMovementDto.from(it, withProduct) },
            page = movements.number,
            size = movements.size,
            totalItems = movements.totalElements,
            totalPages = movements.totalPages,
        )
    }
}

/** The answer to a stock change: the new stock and the line it added to the log. */
data class StockChangeDto(
    val stock: StockDto,
    val movement: StockMovementDto?,
)
