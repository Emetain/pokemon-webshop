package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.dto.SharePage
import com.emetain.pokemonwebshop.dto.SitemapEntry
import org.slf4j.LoggerFactory
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import org.springframework.web.util.HtmlUtils
import org.springframework.web.util.UriUtils
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * What search engines and link previews read without running JavaScript: the sitemap, robots.txt and the built
 * `index.html` with the title, description and Open Graph tags of one page filled in. Every value is escaped.
 */
@Service
class SeoService(
    private val properties: AppProperties,
    private val resourceLoader: ResourceLoader,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val baseUrl: String get() = properties.baseUrl.trimEnd('/')

    /** The built index.html, read once. Null when `app.seo.index-file` isn't set or can't be read. */
    private val indexHtml: String? by lazy {
        val location = properties.seo.indexFile.takeIf { it.isNotBlank() } ?: return@lazy null
        val resource = resourceLoader.getResource(if (location.contains(":")) location else "file:$location")
        val html = runCatching { resource.inputStream.use { String(it.readAllBytes(), Charsets.UTF_8) } }
            .onFailure { log.warn("Can't read the index file for link previews at {}: {}", location, it.message) }
            .getOrNull() ?: return@lazy null
        html.takeIf { it.contains(START) && it.contains(END) }
            ?: null.also { log.warn("The index file at {} has no page-meta markers", location) }
    }

    fun sitemap(entries: List<SitemapEntry>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""").append('\n')
        for (entry in entries.distinctBy { it.path }) {
            append("  <url><loc>").append(xml(baseUrl + entry.path)).append("</loc>")
            entry.lastModified?.let { append("<lastmod>").append(DATE.format(it.atOffset(ZoneOffset.UTC))).append("</lastmod>") }
            append("</url>\n")
        }
        append("</urlset>\n")
    }

    /**
     * For the live shop. Pages with a customer's own data stay out; the search page and the 404 page are left to
     * their `noindex` tag, which a search engine can only read when it may open the page.
     */
    fun robots(): String = buildString {
        append("User-agent: *\n")
        PRIVATE_PATHS.forEach { append("Disallow: ").append(it).append('\n') }
        append("Disallow: /api/\n")
        append("Allow: /api/v1/media/\n")
        append("\nSitemap: ").append(baseUrl).append("/sitemap.xml\n")
    }

    /** The app's index.html with the tags of [page], or as it is for a page that doesn't exist. Null without one. */
    fun page(page: SharePage?): String? {
        val html = indexHtml ?: return null
        if (page == null) return html
        val url = baseUrl + page.path
        val title = attribute(page.title)
        val description = attribute(plainText(page.description))
        val tags = buildString {
            append("<title>").append(title).append(" | Poketopia</title>\n")
            append("""    <meta name="description" content="$description" />""").append('\n')
            append("""    <link rel="canonical" href="${attribute(url)}" />""").append('\n')
            append("""    <meta property="og:site_name" content="Poketopia" />""").append('\n')
            append("""    <meta property="og:type" content="${attribute(page.type)}" />""").append('\n')
            append("""    <meta property="og:title" content="$title" />""").append('\n')
            append("""    <meta property="og:description" content="$description" />""").append('\n')
            append("""    <meta property="og:url" content="${attribute(url)}" />""").append('\n')
            page.imageUrl?.let { image ->
                val absolute = if (image.startsWith("/")) baseUrl + image else image
                append("""    <meta property="og:image" content="${attribute(absolute)}" />""").append('\n')
                append("""    <meta name="twitter:card" content="summary_large_image" />""").append('\n')
            }
            append("    ")
        }
        val start = html.indexOf(START) + START.length
        val end = html.indexOf(END)
        return html.substring(0, start) + "\n    " + tags + html.substring(end)
    }

    private fun attribute(value: String) = HtmlUtils.htmlEscape(value, Charsets.UTF_8.name())

    private fun xml(value: String) = HtmlUtils.htmlEscape(value, Charsets.UTF_8.name())

    companion object {
        private const val START = "<!-- page-meta -->"
        private const val END = "<!-- /page-meta -->"
        private const val DESCRIPTION_LENGTH = 160
        private val DATE = DateTimeFormatter.ISO_LOCAL_DATE

        /** The same list as the private routes with `noindex` in the frontend's router. */
        val PRIVATE_PATHS = listOf(
            "/admin",
            "/account",
            "/afrekenen",
            "/drops/*/afrekenen",
            "/winkelwagen",
            "/bestelling",
            "/klantenservice/ticket",
            "/collectie/gedeeld",
            "/verlanglijst",
            "/nieuwsbrief",
        )

        /** The URL names of the kinds of product, as `PRODUCT_TYPES` in the frontend's `data/shop.js`. */
        val TYPE_SLUGS: Map<ProductType, String> = mapOf(
            ProductType.BOOSTER_BOX to "booster-boxen",
            ProductType.ELITE_TRAINER_BOX to "elite-trainer-boxen",
            ProductType.BOOSTER_BUNDLE to "booster-bundles",
            ProductType.BOOSTER_PACK to "booster-packs",
            ProductType.SLEEVED_BOOSTER to "sleeved-boosters",
            ProductType.BLISTER to "blisters",
            ProductType.TIN to "tins",
            ProductType.COLLECTION_BOX to "collectieboxen",
            ProductType.STARTER_DECK to "starterdecks",
            ProductType.SINGLE to "losse-kaarten",
            ProductType.GRADED_SLAB to "graded-kaarten",
            ProductType.BULK to "bulk",
            ProductType.MYSTERY_BOX to "mystery-boxen",
        )

        /** A path from segments, each encoded, such as `/kaarten/pokemon/sv08.5`. */
        fun path(vararg segments: String): String =
            segments.joinToString("/", prefix = "/") { UriUtils.encodePathSegment(it, Charsets.UTF_8) }

        /** Plain text for a description: without the marks of news and info pages, cut at a word. */
        fun plainText(text: String): String {
            val plain = text
                .replace(Regex("(?m)^\\s*(#{1,6}|-)\\s+"), "")
                .replace("**", "")
                .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (plain.length <= DESCRIPTION_LENGTH) return plain
            val cut = plain.take(DESCRIPTION_LENGTH - 1)
            val space = cut.lastIndexOf(' ')
            return (if (space > 80) cut.take(space) else cut) + "…"
        }
    }
}
