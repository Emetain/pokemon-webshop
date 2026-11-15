package com.emetain.pokemonwebshop.integration

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeoIntegrationTest : DropTestSupport() {

    private fun body(path: String, accept: MediaType = MediaType.ALL) =
        mockMvc.perform(get(path).accept(accept)).andReturn().response.contentAsString

    @Test
    fun `the sitemap lists the public pages, and no drafts`() {
        val active = createProduct(name = "$word Elite Trainer Box", type = "ELITE_TRAINER_BOX")
        val draft = createProduct(name = "$word Concept", status = "DRAFT")
        val article = mockMvc.perform(post("/v1/admin/news").cookie(admin).json(mapOf("title" to "$word nieuws", "body" to "Tekst", "published" to true)))
            .andExpect(status().isCreated).json()
        val hidden = mockMvc.perform(post("/v1/admin/news").cookie(admin).json(mapOf("title" to "$word verborgen", "body" to "Tekst", "published" to false)))
            .andExpect(status().isCreated).json()
        val drop = createDrop(listOf(stocked(name = "$word Drop Box")["id"].asLong() to 1))
        val draftDrop = createDrop(listOf(stocked(name = "$word Stille Box")["id"].asLong() to 1), publish = false)

        val xml = mockMvc.perform(get("/v1/seo/sitemap.xml").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
            .andReturn().response.contentAsString

        // Well-formed XML.
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        assertContains(xml, "<loc>http://localhost:5173/</loc>")
        assertContains(xml, "<loc>http://localhost:5173/pokemon</loc>")
        assertContains(xml, "<loc>http://localhost:5173/pokemon/elite-trainer-boxen</loc>")
        assertContains(xml, "<loc>http://localhost:5173/kaarten/magic</loc>")
        assertContains(xml, "/product/${active["slug"].asText()}</loc><lastmod>")
        assertContains(xml, "/nieuws/${article["slug"].asText()}</loc>")
        assertContains(xml, "/drops/${drop.slug}</loc>")
        assertFalse(xml.contains("/product/${draft["slug"].asText()}<"))
        assertFalse(xml.contains("/nieuws/${hidden["slug"].asText()}<"))
        assertFalse(xml.contains("/drops/${draftDrop.slug}<"))
        assertFalse(xml.contains("/collectie/gedeeld"), "shared collections stay out")
        assertContains(xml, "/info/verzending</loc>")
        assertFalse(xml.contains("/info/privacybeleid<"), "a legal page that is still a draft stays out")
    }

    @Test
    fun `robots keeps out the private pages and points to the sitemap`() {
        val robots = mockMvc.perform(get("/v1/seo/robots.txt"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
            .andReturn().response.contentAsString

        listOf("/admin", "/account", "/afrekenen", "/winkelwagen", "/bestelling", "/klantenservice/ticket", "/collectie/gedeeld")
            .forEach { assertContains(robots, "Disallow: $it\n") }
        assertContains(robots, "Allow: /api/v1/media/\n")
        assertContains(robots, "Sitemap: http://localhost:5173/sitemap.xml")
        assertFalse(robots.contains("Disallow: /\n"))
    }

    @Test
    fun `a product page carries its own title, text and picture, escaped`() {
        val product = createProduct(name = "$word \"Box\" <script>", extra = mapOf("description" to "Tien boosters & een promo."))
        uploadImage(product["id"].asLong()).andExpect(status().isOk)

        val html = mockMvc.perform(get("/v1/seo/pages/product/${product["slug"].asText()}").accept(MediaType.ALL))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(header().string("Cache-Control", "no-cache"))
            .andReturn().response.contentAsString

        assertContains(html, "<title>$word &quot;Box&quot; &lt;script&gt; | Poketopia</title>")
        assertContains(html, """<meta property="og:title" content="$word &quot;Box&quot; &lt;script&gt;" />""")
        assertContains(html, """<meta name="description" content="Tien boosters &amp; een promo." />""")
        assertContains(html, """<meta property="og:type" content="product" />""")
        assertContains(html, """<meta property="og:url" content="http://localhost:5173/product/${product["slug"].asText()}" />""")
        assertContains(html, """<meta property="og:image" content="http://localhost:5173/api/v1/media/""")
        assertFalse(html.contains("<script>"), "nothing from the product runs")
        assertFalse(html.contains("Standaard"), "the default title and description are replaced")
        assertContains(html, """<script type="module" crossorigin src="/assets/index-test.js"></script>""")
    }

    @Test
    fun `an unknown or draft product gets the app with a 404`() {
        val draft = createProduct(name = "$word Concept", status = "DRAFT")

        listOf("bestaat-niet", draft["slug"].asText()).forEach { slug ->
            val html = mockMvc.perform(get("/v1/seo/pages/product/$slug"))
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().response.contentAsString
            assertContains(html, "<title>Poketopia | Standaard</title>")
            assertContains(html, "/assets/index-test.js")
        }
    }

    @Test
    fun `a drop page shows no price and no stock`() {
        val product = stocked(name = "$word Schaarse Box", quantity = 7)
        val drop = createDrop(listOf(product["id"].asLong() to 3), extra = mapOf("description" to "Om 20:00 live."))
        uploadImage(product["id"].asLong()).andExpect(status().isOk)

        val html = body("/v1/seo/pages/drops/${drop.slug}")

        assertContains(html, """<meta property="og:description" content="Om 20:00 live." />""")
        assertContains(html, """<meta property="og:image" content="http://localhost:5173/api/v1/media/""")
        assertFalse(html.contains("99,95") || html.contains("9995"), "no price")
        assertFalse(Regex("content=\"[^\"]*\\b[37]\\b").containsMatchIn(html.substringAfter("og:title")), "no stock count")
        mockMvc.perform(get("/v1/seo/pages/drops/${createDrop(listOf(stocked(name = "$word Stil")["id"].asLong() to 1), publish = false).slug}"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a news page uses its summary, and an unpublished one is not found`() {
        val article = mockMvc.perform(
            post("/v1/admin/news").cookie(admin).json(mapOf("title" to "$word nieuws", "summary" to "Kort & krachtig", "body" to "## Kop\nTekst", "published" to true)),
        ).andExpect(status().isCreated).json()
        val hidden = mockMvc.perform(post("/v1/admin/news").cookie(admin).json(mapOf("title" to "$word stil", "body" to "Tekst", "published" to false)))
            .andExpect(status().isCreated).json()

        val html = body("/v1/seo/pages/nieuws/${article["slug"].asText()}")
        assertContains(html, """<meta property="og:type" content="article" />""")
        assertContains(html, """<meta name="description" content="Kort &amp; krachtig" />""")
        mockMvc.perform(get("/v1/seo/pages/nieuws/${hidden["slug"].asText()}")).andExpect(status().isNotFound)
    }

    @Test
    fun `the seo endpoints need no login`() {
        mockMvc.perform(get("/v1/seo/sitemap.xml")).andExpect(status().isOk)
        mockMvc.perform(get("/v1/seo/robots.txt")).andExpect(status().isOk)
        assertTrue(body("/v1/seo/pages/product/bestaat-niet").contains("<!doctype html>"))
    }
}
