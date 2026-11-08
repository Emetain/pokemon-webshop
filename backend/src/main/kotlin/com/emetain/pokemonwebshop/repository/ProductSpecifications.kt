package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.AccessoryType
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductImportSource
import com.emetain.pokemonwebshop.domain.ProductLanguage
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.ProductStock
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.domain.ProductWishlistEntry
import com.emetain.pokemonwebshop.domain.StockAlert
import com.emetain.pokemonwebshop.domain.StockMovement
import com.emetain.pokemonwebshop.domain.User
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.time.LocalDate

/** The filters on product lists. A filter without a value is left out. */
object ProductSpecifications {

    fun withStatus(status: ProductStatus?): Specification<Product>? = status?.let {
        Specification { root, _, builder -> builder.equal(root.get<ProductStatus>("status"), it) }
    }

    fun ofGame(game: Game?): Specification<Product>? = game?.let {
        Specification { root, _, builder -> builder.equal(root.get<Game>("game"), it) }
    }

    fun ofType(type: ProductType?): Specification<Product>? = type?.let {
        Specification { root, _, builder -> builder.equal(root.get<ProductType>("type"), it) }
    }

    fun ofAccessoryType(type: AccessoryType?): Specification<Product>? = type?.let {
        Specification { root, _, builder -> builder.equal(root.get<AccessoryType>("accessoryType"), it) }
    }

    fun inLanguage(language: ProductLanguage?): Specification<Product>? = language?.let {
        Specification { root, _, builder -> builder.equal(root.get<ProductLanguage>("language"), it) }
    }

    fun inSets(setIds: Collection<Long>?): Specification<Product>? = setIds?.let { ids ->
        Specification { root, _, builder ->
            if (ids.isEmpty()) builder.disjunction() else root.get<CardSet>("cardSet").get<Long>("id").`in`(ids)
        }
    }

    fun forCard(cardId: Long?): Specification<Product>? = cardId?.let {
        Specification { root, _, builder -> builder.equal(root.get<Any>("card").get<Long>("id"), it) }
    }

    /** On the price a customer pays: the sale price while there is one. Both ends in cents and included. */
    fun pricedBetween(minCents: Long?, maxCents: Long?): Specification<Product>? {
        if (minCents == null && maxCents == null) return null
        return Specification { root, _, builder ->
            val price = shownPrice(root, builder)
            builder.and(
                *listOfNotNull(
                    builder.isNotNull(root.get<Long>("priceCents")),
                    minCents?.let { builder.greaterThanOrEqualTo(price, it) },
                    maxCents?.let { builder.lessThanOrEqualTo(price, it) },
                ).toTypedArray(),
            )
        }
    }

    fun inStock(required: Boolean): Specification<Product>? =
        if (required) Specification { root, _, builder -> buyable(root, builder) } else null

    fun preOrder(required: Boolean): Specification<Product>? =
        if (required) Specification { root, _, builder -> builder.isTrue(root.get("preOrder")) } else null

    fun onSale(required: Boolean): Specification<Product>? =
        if (required) Specification { root, _, builder -> onSalePredicate(root, builder) } else null

    /** [term] is already in `nameSearch` form. */
    fun nameContains(term: String?): Specification<Product>? = term?.let {
        Specification { root, _, builder -> builder.like(root.get("nameSearch"), "%$it%") }
    }

    fun imported(imported: Boolean?): Specification<Product>? = imported?.let {
        Specification { root, _, builder ->
            val source = root.get<ProductImportSource>("importSource")
            if (it) builder.isNotNull(source) else builder.isNull(source)
        }
    }

    fun withoutPrice() = Specification<Product> { root, _, builder -> builder.isNull(root.get<Long>("priceCents")) }

    fun soldOut() = Specification<Product> { root, _, builder -> builder.lessThanOrEqualTo(available(root, builder), 0) }

    /**
     * Products the shop keeps in stock that are running out. A product that never had stock, such as one that was
     * only imported, isn't running out: it simply isn't stocked.
     */
    fun lowStock() = Specification<Product> { root, query, builder ->
        val movements = requireNotNull(query).subquery(Long::class.javaObjectType)
        val movement = movements.from(StockMovement::class.java)
        movements.select(movement.get("id")).where(builder.equal(movement.get<Product>("product"), root))
        builder.and(
            builder.lessThanOrEqualTo(available(root, builder), root.get("lowStockThreshold")),
            builder.exists(movements),
        )
    }

    fun onWishlistOf(userId: Long) = Specification<Product> { root, query, builder ->
        val entries = requireNotNull(query).subquery(Long::class.javaObjectType)
        val entry = entries.from(ProductWishlistEntry::class.java)
        entries.select(entry.get("id")).where(
            builder.equal(entry.get<User>("user").get<Long>("id"), userId),
            builder.equal(entry.get<Product>("product"), root),
        )
        builder.exists(entries)
    }

    /** Always ends on id, because a product has to stay in its place from one page to the next. */
    fun sortedBy(sort: ProductSort) = Specification<Product> { root, query, builder ->
        // The count query for the total runs through here too, and can't have an order.
        if (query != null && query.resultType != Long::class.javaObjectType) {
            val buyableFirst = builder.asc(
                builder.selectCase<Int>().`when`(buyable(root, builder), 0).otherwise(1),
            )
            val withoutPriceLast = builder.asc(
                builder.selectCase<Int>().`when`(builder.isNull(root.get<Long>("priceCents")), 1).otherwise(0),
            )
            val arrived = root.get<Instant>("arrivedAt")
            val newestArrivalFirst = listOf(
                builder.asc(builder.selectCase<Int>().`when`(builder.isNull(arrived), 1).otherwise(0)),
                builder.desc(arrived),
            )
            val newest = builder.desc(root.get<Instant>("createdAt"))
            val price = shownPrice(root, builder)
            val order = when (sort) {
                ProductSort.FEATURED -> listOf(buyableFirst) + newestArrivalFirst + newest
                ProductSort.NEWEST -> listOf(newest)
                ProductSort.ARRIVED -> newestArrivalFirst + newest
                ProductSort.PRICE_LOWEST -> listOf(withoutPriceLast, builder.asc(price), buyableFirst)
                ProductSort.PRICE_HIGHEST -> listOf(withoutPriceLast, builder.desc(price), buyableFirst)
                ProductSort.NAME -> listOf(builder.asc(root.get<String>("nameSearch")))
                ProductSort.POPULAR -> listOf(builder.desc(demand(root, query, builder)), buyableFirst, newest)
                ProductSort.RELEASE -> {
                    val release = root.get<LocalDate>("releaseDate")
                    listOf(
                        builder.asc(builder.selectCase<Int>().`when`(builder.isNull(release), 1).otherwise(0)),
                        builder.asc(release),
                    )
                }
            }
            query.orderBy(order + builder.asc(root.get<Long>("id")))
        }
        null
    }

    private fun stock(root: Root<Product>): Join<Product, ProductStock> {
        @Suppress("UNCHECKED_CAST")
        val existing = root.joins.firstOrNull { it.attribute.name == "stock" } as Join<Product, ProductStock>?
        return existing ?: root.join("stock")
    }

    private fun available(root: Root<Product>, builder: CriteriaBuilder): Expression<Int> {
        val stock = stock(root)
        return builder.diff(stock.get<Int>("onHand"), stock.get<Int>("reserved"))
    }

    /** A customer can buy it: it has a price and something left. Whether it is active is up to the other filters. */
    private fun buyable(root: Root<Product>, builder: CriteriaBuilder): Predicate =
        builder.and(builder.isNotNull(root.get<Long>("priceCents")), builder.greaterThan(available(root, builder), 0))

    private fun shownPrice(root: Root<Product>, builder: CriteriaBuilder): Expression<Long> =
        builder.coalesce(root.get("salePriceCents"), root.get<Long>("priceCents"))

    private fun onSalePredicate(root: Root<Product>, builder: CriteriaBuilder): Predicate {
        val sale = root.get<Long>("salePriceCents")
        return builder.and(builder.isNotNull(sale), builder.lessThan(sale, root.get<Long>("priceCents")))
    }

    /** Until there are orders: how many customers want the product, on their wishlist or waiting for stock. */
    private fun demand(
        root: Root<Product>,
        query: jakarta.persistence.criteria.CriteriaQuery<*>,
        builder: CriteriaBuilder,
    ): Expression<Long> {
        val wishes = query.subquery(Long::class.javaObjectType)
        val wish = wishes.from(ProductWishlistEntry::class.java)
        wishes.select(builder.count(wish)).where(builder.equal(wish.get<Product>("product"), root))
        val alerts = query.subquery(Long::class.javaObjectType)
        val alert = alerts.from(StockAlert::class.java)
        alerts.select(builder.count(alert)).where(builder.equal(alert.get<Product>("product"), root))
        return builder.sum(wishes, alerts)
    }
}

/** How a list of products can be sorted. [param] is the value in the URL. */
enum class ProductSort(val param: String?) {
    /** In stock first, then what came in most recently. */
    FEATURED(null),
    NEWEST("new"),
    ARRIVED("arrived"),
    PRICE_LOWEST("price-low"),
    PRICE_HIGHEST("price-high"),
    NAME("name"),
    POPULAR("popular"),
    RELEASE("release"),
    ;

    companion object {
        fun fromParam(param: String?): ProductSort = entries.firstOrNull { it.param != null && it.param == param } ?: FEATURED
    }
}
