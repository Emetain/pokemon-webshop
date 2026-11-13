package com.emetain.pokemonwebshop.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import java.time.Instant

/** `PENDING` until the admin chose: `ACCEPTED` (a candidate became the picture) or `REJECTED` (none fit). */
enum class SuggestionStatus { PENDING, ACCEPTED, REJECTED }

/** A product at TCGplayer that may show the same card or product. */
@Embeddable
class PictureCandidate(
    @Column(nullable = false)
    val tcgplayerId: Long,

    @Column(nullable = false, length = 300)
    val name: String,

    /** 0 to 100: how well the name matched. */
    @Column(nullable = false)
    val score: Int,
)

/**
 * A card or product without a picture, where the picture search found possible matches at TCGplayer but wasn't
 * sure. The admin picks one, or none: a rejected one doesn't come back in the next search.
 */
@Entity
@Table(
    name = "picture_suggestions",
    indexes = [
        Index(name = "idx_picture_suggestions_status_game", columnList = "status, game"),
        Index(name = "idx_picture_suggestions_card_id", columnList = "card_id"),
        Index(name = "idx_picture_suggestions_product_id", columnList = "product_id"),
    ],
)
class PictureSuggestion(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val game: Game,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    val card: Card? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    val product: Product? = null,

    @ElementCollection
    @BatchSize(size = 50)
    @CollectionTable(name = "picture_suggestion_candidates", joinColumns = [JoinColumn(name = "suggestion_id")])
    @OrderColumn(name = "position")
    val candidates: MutableList<PictureCandidate> = mutableListOf(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SuggestionStatus = SuggestionStatus.PENDING,

    @Column
    var decidedAt: Instant? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
