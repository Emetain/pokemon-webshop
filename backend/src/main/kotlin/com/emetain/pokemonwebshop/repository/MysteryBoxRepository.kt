package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.MysteryBoxBatch
import com.emetain.pokemonwebshop.domain.MysteryBoxDraw
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface MysteryBoxBatchRepository : JpaRepository<MysteryBoxBatch, Long> {

    /** Oldest first: that is the batch boxes are drawn from. */
    @EntityGraph(attributePaths = ["prizes", "prizes.card"])
    @Query("select b from MysteryBoxBatch b where b.product.id = :productId order by b.createdAt asc, b.id asc")
    fun findByProduct(productId: Long): List<MysteryBoxBatch>

    /** Draws wait for each other per product, so two orders can't both get the last box of a kind. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from MysteryBoxBatch b where b.product.id = :productId order by b.createdAt asc, b.id asc")
    fun findLockedByProduct(productId: Long): List<MysteryBoxBatch>

    fun existsByProductId(productId: Long): Boolean
}

interface MysteryBoxDrawRepository : JpaRepository<MysteryBoxDraw, Long> {

    @EntityGraph(attributePaths = ["prize", "prize.batch"])
    @Query("select d from MysteryBoxDraw d where d.orderLine.order.id = :orderId order by d.id")
    fun findByOrder(orderId: Long): List<MysteryBoxDraw>

    @EntityGraph(attributePaths = ["orderLine", "orderLine.order"])
    @Query("select d from MysteryBoxDraw d where d.prize.batch.id = :batchId order by d.drawnAt desc, d.id desc")
    fun findByBatch(batchId: Long): List<MysteryBoxDraw>
}
