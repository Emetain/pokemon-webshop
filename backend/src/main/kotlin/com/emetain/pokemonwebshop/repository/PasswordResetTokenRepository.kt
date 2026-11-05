package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Long> {
    @Query("select t from PasswordResetToken t join fetch t.user where t.tokenHash = :tokenHash")
    fun findByTokenHash(tokenHash: String): PasswordResetToken?

    /** Claims the token. Returns 0 when another request already used it, which keeps it single-use. */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    fun markUsed(id: Long, now: Instant): Int

    @Modifying
    @Query("delete from PasswordResetToken t where t.user.id = :userId")
    fun deleteAllByUserId(userId: Long): Int

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt <= :now or t.usedAt is not null")
    fun deleteExpiredOrUsed(now: Instant): Int
}
