package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.AccessoryType
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.OrderLine
import com.emetain.pokemonwebshop.domain.OrderStatus
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
import jakarta.persistence.criteria.JoinType
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

    /**
     * What customers see in lists. A product of a set that is out of print only shows while it can be bought: the
     * shop can't get those sets any more, but an old box or a single it does have is for sale.
     */
    fun listedInShop() = Specification<Product> { root, _, builder ->
        val set = cardSet(root)
        builder.or(builder.isNull(set.get<Long>("id")), builder.isTrue(set.get("inPrint")), buyable(root, builder))
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

    /**
     * Always ends on id, because a product has to stay in its place from one page to the next. [soldSince] is where
     * "Populair" starts counting sales: the products sold most since then come first.
     */
    fun sortedBy(sort: ProductSort, soldSince: Instant? = null) = Specification<Product> { root, query, builder ->
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
            val set = cardSet(root)
            // A product's own date goes first: a pre-order can have one before its set is in the catalog.
            val release = builder.coalesce(root.get<LocalDate>("releaseDate"), set.get<LocalDate>("releaseDate"))
            // Newest set first, and the products of one set together, the big boxes first.
            val newestSetFirst = listOf(
                builder.asc(builder.selectCase<Int>().`when`(builder.isNull(release), 1).otherwise(0)),
                builder.desc(release),
                builder.desc(builder.coalesce(set.get<Int>("sortOrder"), NO_SORT_ORDER)),
            )
            val biggestFirst = builder.asc(typeRank(root, builder))
            val mostPopularSetFirst = builder.asc(builder.coalesce(set.get<Int>("popularRank"), NOT_POPULAR))
            val price = shownPrice(root, builder)
            val order = when (sort) {
                ProductSort.FEATURED -> listOf(buyableFirst) + newestArrivalFirst + newestSetFirst + biggestFirst + newest
                ProductSort.NEWEST -> newestSetFirst + biggestFirst + newest
                ProductSort.ARRIVED -> newestArrivalFirst + newest
                ProductSort.PRICE_LOWEST -> listOf(withoutPriceLast, builder.asc(price), buyableFirst)
                ProductSort.PRICE_HIGHEST -> listOf(withoutPriceLast, builder.desc(price), buyableFirst)
                ProductSort.NAME -> listOf(builder.asc(root.get<String>("nameSearch")))
                // What customers bought here first. Then, for products without sales, the most popular sets in the
                // market, and within a set what customers here want most.
                ProductSort.POPULAR -> listOf(builder.desc(sold(root, query, builder, soldSince)), mostPopularSetFirst) +
                    newestSetFirst + listOf(builder.desc(demand(root, query, builder)), buyableFirst, biggestFirst, newest)
                ProductSort.RELEASE -> listOf(
                    builder.asc(builder.selectCase<Int>().`when`(builder.isNull(release), 1).otherwise(0)),
                    builder.asc(release),
                    biggestFirst,
                )
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

    private fun cardSet(root: Root<Product>): Join<Product, CardSet> {
        @Suppress("UNCHECKED_CAST")
        val existing = root.joins.firstOrNull { it.attribute.name == "cardSet" } as Join<Product, CardSet>?
        return existing ?: root.join("cardSet", JoinType.LEFT)
    }

    /** The order of [ProductType]: booster boxes first, accessories last. */
    private fun typeRank(root: Root<Product>, builder: CriteriaBuilder): Expression<Int> {
        val rank = builder.selectCase<ProductType, Int>(root.get("type"))
        ProductType.entries.forEach { rank.`when`(it, it.ordinal) }
        return rank.otherwise(ProductType.entries.size)
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

    /** Units sold in paid orders that weren't cancelled, since [since]. */
    private fun sold(
        root: Root<Product>,
        query: jakarta.persistence.criteria.CriteriaQuery<*>,
        builder: CriteriaBuilder,
        since: Instant?,
    ): Expression<Long> {
        val units = query.subquery(Long::class.javaObjectType)
        val line = units.from(OrderLine::class.java)
        val order = line.join<OrderLine, com.emetain.pokemonwebshop.domain.Order>("order")
        val conditions = listOfNotNull(
            builder.equal(line.get<Product>("product"), root),
            order.get<OrderStatus>("status").`in`(OrderStatus.SOLD),
            since?.let { builder.greaterThanOrEqualTo(order.get("paidAt"), it) },
        )
        units.select(builder.coalesce(builder.sum(line.get<Int>("quantity")).`as`(Long::class.javaObjectType), 0L))
            .where(*conditions.toTypedArray())
        return units
    }

    /** How many customers want the product, on their wishlist or waiting for stock. */
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

    private const val NO_SORT_ORDER = -1
    private const val NOT_POPULAR = Int.MAX_VALUE
}

/** How a list of products can be sorted. [param] is the value in the URL. */
enum class ProductSort(val param: String?) {
    /** In stock first, then what came in most recently, then the newest sets. */
    FEATURED(null),

    /** By release date: the product's own, or else its set's. */
    NEWEST("new"),
    ARRIVED("arrived"),
    PRICE_LOWEST("price-low"),
    PRICE_HIGHEST("price-high"),
    NAME("name"),

    /** What sold most here first, then the sets on `app.shop.popular-sets`, then the newest sets. */
    POPULAR("popular"),

    /** Soonest first, for pre-orders. */
    RELEASE("release"),
    ;

    companion object {
        fun fromParam(param: String?): ProductSort = entries.firstOrNull { it.param != null && it.param == param } ?: FEATURED
    }
}
