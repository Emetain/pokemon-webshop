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

/** A login session. Only the SHA-256 hash of the cookie token is stored. */
@Entity
@Table(
    name = "user_sessions",
    indexes = [
        Index(name = "idx_user_sessions_user_id", columnList = "user_id"),
        Index(name = "idx_user_sessions_expires_at", columnList = "expires_at"),
    ],
)
class UserSession(
    @Column(nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val expiresAt: Instant,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
