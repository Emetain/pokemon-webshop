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

/**
 * One call to OpenAI and what it cost, from the tokens OpenAI reported. The shop adds these up per month and stops
 * at the monthly limit. [costMicros] is in millionths of a US dollar.
 */
@Entity
@Table(name = "ai_usage", indexes = [Index(name = "idx_ai_usage_created_at", columnList = "created_at")])
class AiUsage(
    /** `TEXT` or `IMAGE` for hero slides, `CHAT` for the chatbot. */
    @Column(nullable = false, length = 10)
    val kind: String,

    @Column(nullable = false, length = 60)
    val model: String,

    @Column(nullable = false)
    val inputTokens: Long,

    @Column(nullable = false)
    val outputTokens: Long,

    @Column(nullable = false)
    val costMicros: Long,

    @Column(nullable = false)
    val createdAt: Instant,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
