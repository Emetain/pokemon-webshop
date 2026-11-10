package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.OrderStockState
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant

data class RevenueRow(val orders: Long, val totalCents: Long, val refundedCents: Long)

/** How many of a product someone has in a drop's orders. */
data class DropQuantityRow(val productId: Long, val quantity: Long)

/** Sales of one product in a drop, for the report. */
data class DropSalesRow(val productId: Long, val quantity: Long, val revenueCents: Long)

interface OrderRepository : JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @EntityGraph(attributePaths = ["lines", "user"])
    fun findByNumber(number: String): Order?

    /**
     * Every change to an order goes through this lock: the webhook, the reservation job and the admin can reach the
     * same order at the same moment, and only one of them may convert or release its stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    fun findLockedById(id: Long): Order?

    fun findByStripeSessionId(sessionId: String): Order?

    @EntityGraph(attributePaths = ["user"])
    override fun findAll(spec: Specification<Order>?, pageable: Pageable): Page<Order>

    /** Orders a customer sees in the account: only those that were paid, not checkouts that were given up. */
    @Query("select o from Order o where o.user.id = :userId and o.paidAt is not null order by o.createdAt desc, o.id desc")
    fun findPaidByUser(userId: Long, pageable: Pageable): Page<Order>

    @Query("select o.id from Order o where o.cartId = :cartId and o.status = :status")
    fun findIdsByCartAndStatus(cartId: Long, status: OrderStatus = OrderStatus.PENDING_PAYMENT): List<Long>

    @Query(
        "select o.id from Order o where o.status = :status and o.stockState = :state and o.reservedUntil < :now " +
            "order by o.reservedUntil",
    )
    fun findExpiredReservations(
        now: Instant,
        status: OrderStatus = OrderStatus.PENDING_PAYMENT,
        state: OrderStockState = OrderStockState.RESERVED,
    ): List<Long>

    @Query(
        "select new com.emetain.pokemonwebshop.repository.RevenueRow(count(o), coalesce(sum(o.totalCents), 0), " +
            "coalesce(sum(o.refundedCents), 0)) from Order o where o.paidAt >= :from",
    )
    fun revenueSince(from: Instant): RevenueRow

    fun countByStatusIn(statuses: Collection<OrderStatus>): Long

    fun existsByNumber(number: String): Boolean

    @Query("select o.id from Order o where o.dropEntryId = :entryId and o.status = :status")
    fun findIdsByDropEntryAndStatus(entryId: Long, status: OrderStatus = OrderStatus.PENDING_PAYMENT): List<Long>

    /**
     * What someone has in a drop's orders that count: paid, or waiting for payment with the stock held. "Someone" is
     * every order with the same account, email, address or waiting room entry, so a second account with the same
     * address or a +tag on the email doesn't get around the limits. A null value matches nothing.
     */
    @Query(
        "select new com.emetain.pokemonwebshop.repository.DropQuantityRow(l.product.id, sum(l.quantity)) " +
            "from OrderLine l join l.order o left join o.user u " +
            "where o.drop.id = :dropId and o.status in :statuses " +
            "and (u.id = :userId or o.emailKey = :emailKey or o.addressKey = :addressKey or o.dropEntryId = :entryId) " +
            "group by l.product.id",
    )
    fun dropQuantities(
        dropId: Long,
        userId: Long?,
        emailKey: String?,
        addressKey: String?,
        entryId: Long?,
        statuses: Collection<OrderStatus> = DROP_LIMIT_STATUSES,
    ): List<DropQuantityRow>

    @Query(
        "select new com.emetain.pokemonwebshop.repository.DropSalesRow(l.product.id, sum(l.quantity), sum(l.lineTotalCents)) " +
            "from OrderLine l join l.order o where o.drop.id = :dropId and o.status in :statuses group by l.product.id",
    )
    fun dropSales(dropId: Long, statuses: Collection<OrderStatus> = OrderStatus.SOLD): List<DropSalesRow>

    /** Buyers: different email addresses with a paid order in the drop. */
    @Query("select count(distinct o.emailKey) from Order o where o.drop.id = :dropId and o.status in :statuses")
    fun countDropBuyers(dropId: Long, statuses: Collection<OrderStatus> = OrderStatus.SOLD): Long

    @Query("select count(o) from Order o where o.drop.id = :dropId and o.status in :statuses")
    fun countDropOrders(dropId: Long, statuses: Collection<OrderStatus> = OrderStatus.SOLD): Long

    @Query("select count(o) from Order o where o.drop.id = :dropId and o.paidAt is not null and o.status in :statuses")
    fun countDropPaidOrdersWithStatus(dropId: Long, statuses: Collection<OrderStatus>): Long

    companion object {
        /** Orders that count towards drop limits. Cancelled, refunded and unpaid checkouts that ran out don't. */
        val DROP_LIMIT_STATUSES: Set<OrderStatus> = OrderStatus.SOLD + OrderStatus.PENDING_PAYMENT
    }
}
