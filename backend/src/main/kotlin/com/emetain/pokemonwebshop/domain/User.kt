package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.ColumnDefault
import java.time.Instant

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_users_collection_share_slug", columnNames = ["collection_share_slug"]),
        UniqueConstraint(name = "uk_users_stripe_customer_id", columnNames = ["stripe_customer_id"]),
    ],
)
class User(
    @Column(nullable = false, unique = true, length = 254)
    var email: String,

    @Column(nullable = false, length = 100)
    var passwordHash: String,

    @Column(nullable = false, length = 100)
    var displayName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: Role = Role.CUSTOMER,

    @Column(nullable = false, length = 2)
    var country: String = DEFAULT_COUNTRY,

    /** The currency card prices are shown in. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    var currency: Currency = Currency.EUR,

    /** What the binder on the home page shows. Null until the customer chooses, see [BinderSource.DEFAULT]. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var binderSource: BinderSource? = null,

    /** The random part of the public link to this collection. Null means the collection is private. */
    @Column(length = 64)
    var collectionShareSlug: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    /** The customer at Stripe for Premium (phase 11). Orders don't use it. */
    @Column(length = 255)
    var stripeCustomerId: String? = null,

    /** Premium, see [com.emetain.pokemonwebshop.service.PremiumAccess]. Admins have it from their role instead. */
    @Column
    var premiumSince: Instant? = null,

    @Column
    var premiumUntil: Instant? = null,

    // The defaults let Hibernate add these columns to a local database that already has users.
    @Column(nullable = false)
    @ColumnDefault("false")
    var premiumForever: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var premiumSource: PremiumSource? = null,

    /** Card scans that gave a result without Premium. 100 are free, for good (phase 11). */
    @Column(nullable = false)
    @ColumnDefault("0")
    var freeScansUsed: Int = 0,

    /** When the customer agreed to the last Premium checkout's terms; copied to the subscription it creates. */
    @Column
    var premiumConsentAt: Instant? = null,

    /** Light, dark or the device's setting (phase 12, free for everyone). Null: light, the shop's own look. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    var colorMode: ColorMode? = null,

    /** The colour theme (phase 12). Every theme but [ColorTheme.POKETOPIA] is Premium. Null: Poketopia. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var colorTheme: ColorTheme? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
