package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?

    fun findByStripeCustomerId(stripeCustomerId: String): User?

    /** Serialises Premium changes and scan counts of one user: webhooks and scans can come in at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    fun findByIdForUpdate(id: Long): User?

    fun existsByEmail(email: String): Boolean

    fun findByCollectionShareSlug(slug: String): User?

    /** Everyone with Premium right now, paid or given, newest first. Admins have it from their role and aren't listed. */
    @Query(
        "select u from User u where u.role <> :admin and (u.premiumForever = true or u.premiumUntil > :now) " +
            "order by u.premiumSince desc nulls last, u.id desc",
    )
    fun findPremiumMembers(now: Instant, admin: Role = Role.ADMIN): List<User>
}
