package com.emetain.pokemonwebshop.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
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
import jakarta.persistence.OneToOne
import jakarta.persistence.OrderColumn
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "products",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_products_slug", columnNames = ["slug"]),
        UniqueConstraint(name = "uk_products_import", columnNames = ["import_source", "import_key"]),
        UniqueConstraint(name = "uk_products_stock_id", columnNames = ["stock_id"]),
    ],
    indexes = [
        Index(name = "idx_products_status_game_type", columnList = "status, game, type"),
        Index(name = "idx_products_set_id", columnList = "set_id"),
        Index(name = "idx_products_card_id", columnList = "card_id"),
        Index(name = "idx_products_created_at", columnList = "created_at"),
        Index(name = "idx_products_arrived_at", columnList = "arrived_at"),
        Index(name = "idx_products_name_search", columnList = "name_search"),
    ],
)
class Product(
    /** Null for accessories that fit every game. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var game: Game?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var type: ProductType,

    @Column(nullable = false, length = 255)
    var name: String,

    /** The name without accents and punctuation, for search. */
    @Column(nullable = false, length = 255)
    var nameSearch: String,

    @Column(nullable = false, length = 200)
    var slug: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = [CascadeType.ALL])
    @JoinColumn(name = "stock_id", nullable = false)
    val stock: ProductStock = ProductStock(),

    /** Only for [ProductType.ACCESSORY]. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var accessoryType: AccessoryType? = null,

    /** Plain text. Line breaks are kept. */
    @Column(length = 10000)
    var description: String? = null,

    /** Null for products without cards, such as most accessories. */
    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    var language: ProductLanguage? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_id")
    var cardSet: CardSet? = null,

    /** For singles and slabs. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    var card: Card? = null,

    /** For singles: which finish of the card this is, such as `holo` or `reverse`. See [CardVariants]. */
    @Column(length = 30)
    var cardVariant: String? = null,

    /** For singles. */
    @Enumerated(EnumType.STRING)
    @Column(name = "card_condition", length = 20)
    var condition: CardCondition? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    var gradingCompany: GradingCompany? = null,

    @Column(length = 20)
    var grade: String? = null,

    /** In euro cents, including VAT. Null until the shop sets a price: the product can't be bought until then. */
    @Column
    var priceCents: Long? = null,

    /** Lower than [priceCents] while the product is on sale. */
    @Column
    var salePriceCents: Long? = null,

    /** Image URLs, the first one is the main image. */
    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = [JoinColumn(name = "product_id")])
    @OrderColumn(name = "position")
    @Column(name = "url", nullable = false, length = 500)
    val images: MutableList<String> = mutableListOf(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ProductStatus = ProductStatus.DRAFT,

    @Column(nullable = false)
    var preOrder: Boolean = false,

    @Column
    var releaseDate: LocalDate? = null,

    /** When pre-orders go out. Can be later than the release date. */
    @Column
    var shipsFrom: LocalDate? = null,

    /** When the product last went from sold out to in stock. "Nieuw binnen" on the home page goes by this. */
    @Column
    var arrivedAt: Instant? = null,

    /** The shop gets a warning, and the product shows "Laatste X", once this many or fewer are left. */
    @Column(nullable = false)
    var lowStockThreshold: Int = DEFAULT_LOW_STOCK_THRESHOLD,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var importSource: ProductImportSource? = null,

    /** The id of the product at [importSource]. */
    @Column(length = 64)
    var importKey: String? = null,

    /** An import never changes a product the admin edited. */
    @Column(nullable = false)
    var editedByAdmin: Boolean = false,

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "products_seq")
    @SequenceGenerator(name = "products_seq", sequenceName = "products_seq", allocationSize = 50)
    val id: Long = 0,
) {
    companion object {
        const val DEFAULT_LOW_STOCK_THRESHOLD = 2
    }
}
