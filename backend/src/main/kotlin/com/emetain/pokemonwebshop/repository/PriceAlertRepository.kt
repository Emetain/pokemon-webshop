package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.PriceAlert
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PriceAlertRepository : JpaRepository<PriceAlert, Long> {

    @Query("select a from PriceAlert a join fetch a.card c join fetch c.set where a.user.id = :userId order by a.createdAt desc, a.id desc")
    fun findAllOfUser(userId: Long): List<PriceAlert>

    @Query("select a from PriceAlert a where a.user.id = :userId and a.card.id = :cardId order by a.createdAt, a.id")
    fun findAllByUserIdAndCardId(userId: Long, cardId: Long): List<PriceAlert>

    fun countByUserId(userId: Long): Long

    fun findByIdAndUserId(id: Long, userId: Long): PriceAlert?

    fun findByOffTokenHash(hash: String): PriceAlert?

    @Query("select distinct a.user.id from PriceAlert a")
    fun findUserIds(): List<Long>
}
