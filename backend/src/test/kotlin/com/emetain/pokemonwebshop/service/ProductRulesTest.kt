package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.ProductStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductRulesTest {

    @Test
    fun `a sale price only counts while it is lower than the normal price`() {
        assertTrue(ProductRules.onSale(1000, 800))
        assertFalse(ProductRules.onSale(1000, 1000))
        assertFalse(ProductRules.onSale(1000, 1200))
        assertFalse(ProductRules.onSale(1000, null))
        assertFalse(ProductRules.onSale(null, 800))
    }

    @Test
    fun `the shown price is the sale price while on sale`() {
        assertEquals(800, ProductRules.shownPriceCents(1000, 800))
        assertEquals(1000, ProductRules.shownPriceCents(1000, 1200))
        assertEquals(1000, ProductRules.shownPriceCents(1000, null))
        assertNull(ProductRules.shownPriceCents(null, null))
    }

    @ParameterizedTest
    @CsvSource("1000, 750, 25", "4495, 3995, 11", "999, 998, 0", "1000, 1, 99")
    fun `the discount is rounded down`(price: Long, sale: Long, percent: Int) {
        assertEquals(percent, ProductRules.discountPercent(price, sale))
    }

    @Test
    fun `no discount without a sale`() {
        assertNull(ProductRules.discountPercent(1000, null))
        assertNull(ProductRules.discountPercent(1000, 1000))
    }

    @Test
    fun `a product can be bought when it is active, has a price and has something left`() {
        assertTrue(ProductRules.canBuy(ProductStatus.ACTIVE, 1000, 1))
        assertFalse(ProductRules.canBuy(ProductStatus.ACTIVE, 1000, 0))
        assertFalse(ProductRules.canBuy(ProductStatus.ACTIVE, 1000, -1))
        assertFalse(ProductRules.canBuy(ProductStatus.ACTIVE, null, 5))
        assertFalse(ProductRules.canBuy(ProductStatus.DRAFT, 1000, 5))
        assertFalse(ProductRules.canBuy(ProductStatus.ARCHIVED, 1000, 5))
    }

    @Test
    fun `last few shows from the threshold down to one`() {
        assertEquals(2, ProductRules.lastFew(2, 2))
        assertEquals(1, ProductRules.lastFew(1, 2))
        assertNull(ProductRules.lastFew(3, 2))
        assertNull(ProductRules.lastFew(0, 2))
        assertNull(ProductRules.lastFew(1, 0))
    }
}

class ProductSlugsTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "Scarlet & Violet: 151 Elite Trainer Box|scarlet-violet-151-elite-trainer-box",
            "Pokémon Mini Tin|pokemon-mini-tin",
            "Archazia's Island Booster Box|archazia-s-island-booster-box",
            "  --Zoro!!--  |zoro",
            "ポケモン|product",
        ],
    )
    fun `names become readable slugs`(name: String, slug: String) {
        assertEquals(slug, ProductSlugs.of(name))
    }

    @Test
    fun `a taken slug gets a number`() {
        val taken = setOf("booster-box", "booster-box-2")
        assertEquals("booster-box-3", ProductSlugs.unique("Booster Box") { it in taken })
        assertEquals("tin", ProductSlugs.unique("Tin") { it in taken })
    }

    @Test
    fun `paths of the API can't be product slugs`() {
        assertEquals("new-arrivals-2", ProductSlugs.unique("New arrivals") { false })
        assertFalse(ProductSlugs.isValid("bestsellers"))
    }

    @Test
    fun `slugs are capped and never end on a dash`() {
        val slug = ProductSlugs.of("a".repeat(179) + " b c")
        assertTrue(slug.length <= 180)
        assertFalse(slug.endsWith("-"))
    }

    @Test
    fun `only slugs in their own form are valid`() {
        assertTrue(ProductSlugs.isValid("destined-rivals-etb"))
        assertFalse(ProductSlugs.isValid("Destined-Rivals"))
        assertFalse(ProductSlugs.isValid("destined rivals"))
        assertFalse(ProductSlugs.isValid("-etb"))
        assertFalse(ProductSlugs.isValid(""))
    }
}
