package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/** "Herinner mij": one mail shortly before the drop starts, so there is time to join the waiting room. */
@Entity
@Table(
    name = "drop_reminders",
    uniqueConstraints = [UniqueConstraint(name = "uk_drop_reminders_drop_email", columnNames = ["drop_id", "email"])],
)
class DropReminder(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drop_id", nullable = false)
    val drop: Drop,

    @Column(nullable = false, length = 254)
    val email: String,

    @Column(nullable = false)
    val createdAt: Instant,

    @Column
    var sentAt: Instant? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
