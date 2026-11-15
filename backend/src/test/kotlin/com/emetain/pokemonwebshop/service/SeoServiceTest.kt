package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.dto.SitemapEntry
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeoServiceTest {

    private val service = SeoService(testProperties(), DefaultResourceLoader())

    @Test
    fun `a description loses its marks and is cut at a word`() {
        assertEquals("Kop Een lijst met vet en een link.", SeoService.plainText("## Kop\n- Een lijst met **vet** en een [link](https://example.com)."))
        val long = SeoService.plainText("woord ".repeat(60))
        assertTrue(long.length <= 160)
        assertTrue(long.endsWith("woord…"))
        assertEquals("Pokémon-collectie", SeoService.plainText("Pokémon-collectie"))
    }

    @Test
    fun `sitemap addresses are encoded and escaped`() {
        val xml = service.sitemap(
            listOf(
                SitemapEntry(SeoService.path("kaarten", "pokemon", "sv08.5")),
                SitemapEntry(SeoService.path("product", "a&b c"), Instant.parse("2026-11-15T10:00:00Z")),
                SitemapEntry(SeoService.path("kaarten", "pokemon", "sv08.5")),
            ),
        )

        assertContains(xml, "<loc>http://localhost:5173/kaarten/pokemon/sv08.5</loc>")
        assertContains(xml, "<loc>http://localhost:5173/product/a&amp;b%20c</loc><lastmod>2026-11-15</lastmod>")
        assertEquals(1, Regex("sv08\\.5").findAll(xml).count(), "a path is listed once")
    }

    @Test
    fun `without an index file there is no page to fill in`() {
        assertNull(service.page(null))
    }
}
