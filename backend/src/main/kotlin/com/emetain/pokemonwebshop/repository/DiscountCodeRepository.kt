package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.DiscountCode
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface DiscountCodeRepository : JpaRepository<DiscountCode, Long> {

    fun findByCode(code: String): DiscountCode?

    fun existsByCode(code: String): Boolean

    /** Checkouts with the same code wait for each other, so two can't both take its last use. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DiscountCode d where d.code = :code")
    fun findLockedByCode(code: String): DiscountCode?

    fun findAllByOrderByCreatedAtDesc(): List<DiscountCode>
}
