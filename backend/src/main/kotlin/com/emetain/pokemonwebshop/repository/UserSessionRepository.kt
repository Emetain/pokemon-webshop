package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.UserSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface UserSessionRepository : JpaRepository<UserSession, Long> {
    @Query("select s from UserSession s join fetch s.user where s.tokenHash = :tokenHash and s.expiresAt > :now")
    fun findActive(tokenHash: String, now: Instant): UserSession?

    @Modifying
    @Query("delete from UserSession s where s.tokenHash = :tokenHash")
    fun deleteByTokenHash(tokenHash: String): Int

    @Modifying
    @Query("delete from UserSession s where s.user.id = :userId")
    fun deleteAllByUserId(userId: Long): Int

    @Modifying
    @Query("delete from UserSession s where s.expiresAt <= :now")
    fun deleteExpired(now: Instant): Int
}
