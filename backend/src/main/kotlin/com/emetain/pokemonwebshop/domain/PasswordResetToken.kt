package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/** A single-use password reset. Only the SHA-256 hash of the emailed token is stored. */
@Entity
@Table(
    name = "password_reset_tokens",
    indexes = [
        Index(name = "idx_password_reset_tokens_user_id", columnList = "user_id"),
        Index(name = "idx_password_reset_tokens_expires_at", columnList = "expires_at"),
    ],
)
class PasswordResetToken(
    @Column(nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val expiresAt: Instant,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column
    var usedAt: Instant? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
