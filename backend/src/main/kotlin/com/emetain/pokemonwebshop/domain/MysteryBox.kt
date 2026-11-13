package com.emetain.pokemonwebshop.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant

/**
 * A batch of mystery boxes the shop packed by hand, such as 100 boxes: 1 with a big hit, 9 with an Elite Trainer Box
 * and 90 with packs. Its [prizes] say what is in it, so the shop can show the real chances. A paid order draws a
 * random box that is left, from the oldest batch first, so the chances on the page stay true until the last box.
 * Each box is one unit of the product's stock.
 */
@Entity
@Table(
    name = "mystery_box_batches",
    indexes = [Index(name = "idx_mystery_box_batches_product_id", columnList = "product_id")],
)
class MysteryBoxBatch(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @OneToMany(mappedBy = "batch", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("valueCents DESC, id ASC")
    val prizes: MutableList<MysteryBoxPrize> = mutableListOf(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    val boxCount: Int get() = prizes.sumOf { it.count }

    val remaining: Int get() = prizes.sumOf { it.remaining }
}

/** One kind of box in a batch: what is in it, what that is worth, and how many of these boxes were packed. */
@Entity
@Table(
    name = "mystery_box_prizes",
    indexes = [Index(name = "idx_mystery_box_prizes_batch_id", columnList = "batch_id")],
)
class MysteryBoxPrize(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    val batch: MysteryBoxBatch,

    /** What the customer finds, such as "Charizard ex (Special Illustration Rare)" or "3 booster packs". */
    @Column(nullable = false, length = 150)
    val name: String,

    /** What the contents are worth, in euro cents, as the shop estimates it. */
    @Column(nullable = false)
    val valueCents: Long,

    /** How many boxes of this kind are in the batch. */
    @Column(name = "box_count", nullable = false)
    val count: Int,

    /** A card from the catalog, to show its picture. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    val card: Card? = null,

    /** How many of these boxes went to paid orders. */
    @Column(nullable = false)
    var drawn: Int = 0,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    val remaining: Int get() = count - drawn
}

/** Which box a paid order got. A cancelled order puts its boxes back. */
@Entity
@Table(
    name = "mystery_box_draws",
    indexes = [
        Index(name = "idx_mystery_box_draws_order_line_id", columnList = "order_line_id"),
        Index(name = "idx_mystery_box_draws_prize_id", columnList = "prize_id"),
    ],
)
class MysteryBoxDraw(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prize_id", nullable = false)
    val prize: MysteryBoxPrize,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_line_id", nullable = false)
    val orderLine: OrderLine,

    @Column(nullable = false)
    val drawnAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
