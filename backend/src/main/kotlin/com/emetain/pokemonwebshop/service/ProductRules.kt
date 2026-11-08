package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductStatus
import java.text.Normalizer
import java.util.Locale

/** What a customer sees of a product's price and stock. Pure, so it can be tested without a database. */
object ProductRules {

    /** A sale price only counts while it is lower than the normal price. */
    fun onSale(priceCents: Long?, salePriceCents: Long?): Boolean =
        priceCents != null && salePriceCents != null && salePriceCents < priceCents

    /** The price a customer pays, or null when the shop hasn't set one yet. */
    fun shownPriceCents(priceCents: Long?, salePriceCents: Long?): Long? =
        if (onSale(priceCents, salePriceCents)) salePriceCents else priceCents

    /** Rounded down, so "−25%" never promises more than the real discount. */
    fun discountPercent(priceCents: Long?, salePriceCents: Long?): Int? {
        if (!onSale(priceCents, salePriceCents)) return null
        return ((priceCents!! - salePriceCents!!) * 100 / priceCents).toInt()
    }

    /** In the shop, with a price and at least one left. */
    fun canBuy(status: ProductStatus, priceCents: Long?, available: Int): Boolean =
        status == ProductStatus.ACTIVE && priceCents != null && available > 0

    /** "Laatste X": only while it can be bought and the stock is at or below the warning level. */
    fun lastFew(available: Int, threshold: Int): Int? = available.takeIf { it in 1..threshold }

    fun canBuy(product: Product): Boolean = canBuy(product.status, product.priceCents, product.stock.available)

    fun shownPriceCents(product: Product): Long? = shownPriceCents(product.priceCents, product.salePriceCents)
}

/** Readable, unique URLs for products. */
object ProductSlugs {

    private const val MAX_LENGTH = 180

    private val ACCENTS = Regex("\\p{M}+")
    private val NOT_A_WORD = Regex("[^a-z0-9]+")

    /** "Scarlet & Violet: 151 Elite Trainer Box" becomes "scarlet-violet-151-elite-trainer-box". */
    fun of(name: String): String {
        val slug = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(ACCENTS, "")
            .lowercase(Locale.ROOT)
            .replace(NOT_A_WORD, "-")
            .trim('-')
            .take(MAX_LENGTH)
            .trimEnd('-')
        return slug.ifEmpty { "product" }
    }

    /** The slug of [name], with -2, -3 and so on after it while [taken] says it's in use. */
    fun unique(name: String, taken: (String) -> Boolean): String {
        val base = of(name)
        val free = { slug: String -> slug !in RESERVED && !taken(slug) }
        if (free(base)) return base
        return generateSequence(2) { it + 1 }.map { "$base-$it" }.first(free)
    }

    fun isValid(slug: String): Boolean = slug.length in 1..MAX_LENGTH && slug == of(slug) && slug !in RESERVED

    /** Paths next to `/products/{slug}` in the API. A product with one of these names would be unreachable. */
    private val RESERVED = setOf("new-arrivals", "bestsellers")
}
