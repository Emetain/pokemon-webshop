package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?

    fun findByStripeCustomerId(stripeCustomerId: String): User?

    /** Serialises Premium changes and scan counts of one user: webhooks and scans can come in at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    fun findByIdForUpdate(id: Long): User?

    fun existsByEmail(email: String): Boolean

    fun findByCollectionShareSlug(slug: String): User?
}
