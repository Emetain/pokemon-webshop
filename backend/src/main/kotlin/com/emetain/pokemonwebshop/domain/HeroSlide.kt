package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A slide in the hero carousel on the home page, managed in admin: a promotion, an upcoming set or other news.
 * The hero with the binder always comes first, these follow in the order of [position].
 */
@Entity
@Table(name = "hero_slides")
class HeroSlide(
    @Column(nullable = false, length = 120)
    var title: String,

    @Column(nullable = false)
    var position: Int,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(length = 300)
    var text: String? = null,

    @Column(length = 40)
    var buttonLabel: String? = null,

    /** A path in the shop such as `/pokemon/booster-boxen`, or a full URL. */
    @Column(length = 300)
    var linkUrl: String? = null,

    @Column(length = 500)
    var imageUrl: String? = null,

    /** Gives the slide the colour of its game. Null keeps the brand blue. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var game: Game? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    /** The slide shows from this moment. Null means right away. */
    @Column
    var startsAt: Instant? = null,

    /** The slide stops showing at this moment. Null means until it's switched off. */
    @Column
    var endsAt: Instant? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    fun showsAt(moment: Instant): Boolean =
        active && (startsAt == null || !moment.isBefore(startsAt)) && (endsAt == null || moment.isBefore(endsAt))
}
