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
}
