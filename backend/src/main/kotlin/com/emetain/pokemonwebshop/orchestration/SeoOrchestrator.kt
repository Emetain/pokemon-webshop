package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.dto.SharePage
import com.emetain.pokemonwebshop.dto.SitemapEntry
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.DropRepository
import com.emetain.pokemonwebshop.repository.InfoPageRepository
import com.emetain.pokemonwebshop.repository.NewsArticleRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.repository.SlugRow
import com.emetain.pokemonwebshop.service.SeoService
import com.emetain.pokemonwebshop.service.SeoService.Companion.path
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * The sitemap, robots.txt, and the pages that link previews read. A link preview shows a name, a text and a
 * picture: never a price of a drop, and never how many of something are left.
 */
@Component
class SeoOrchestrator(
    private val seoService: SeoService,
    private val productRepository: ProductRepository,
    private val dropRepository: DropRepository,
    private val newsArticleRepository: NewsArticleRepository,
    private val infoPageRepository: InfoPageRepository,
    private val cardSetRepository: CardSetRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun sitemap(): ResponseEntity<String> {
        val fixed = listOf("/", "/producten", "/accessoires", "/drops", "/nieuws", "/pre-order", "/releasekalender", "/collectie", "/collectie/pokedex")
            .map { SitemapEntry(it) }
        val games = Game.entries.flatMap { listOf(SitemapEntry(path(it.slug)), SitemapEntry(path("kaarten", it.slug))) }
        val gameTypes = productRepository.gameTypes().mapNotNull { row ->
            SeoService.TYPE_SLUGS[row.type]?.let { SitemapEntry(path(row.game.slug, it)) }
        }
        fun pages(prefix: String, rows: List<SlugRow>) = rows.map { SitemapEntry(path(prefix, it.slug), it.updatedAt) }
        val xml = seoService.sitemap(
            fixed + games + gameTypes +
                pages("product", productRepository.sitemapRows()) +
                pages("drops", dropRepository.sitemapRows()) +
                pages("nieuws", newsArticleRepository.sitemapRows(clock.instant())) +
                pages("info", infoPageRepository.sitemapRows()) +
                cardSetRepository.sitemapRows().map { SitemapEntry(path("kaarten", it.game.slug, it.code)) },
        )
        return text(xml, MediaType(MediaType.APPLICATION_XML, Charsets.UTF_8))
    }

    fun robots(): ResponseEntity<String> = text(seoService.robots(), MediaType(MediaType.TEXT_PLAIN, Charsets.UTF_8))

    /** With its own type, whatever the client asks for: search engines don't all send a fitting Accept header. */
    private fun text(body: String, type: MediaType): ResponseEntity<String> =
        ResponseEntity.ok().contentType(type).cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic()).body(body)

    @Transactional(readOnly = true)
    fun productPage(slug: String): ResponseEntity<String> {
        val product = productRepository.findBySlug(slug)?.takeIf { it.status == ProductStatus.ACTIVE }
        return html(
            product?.let {
                SharePage(
                    path = path("product", it.slug),
                    title = it.name,
                    description = it.description?.takeIf(String::isNotBlank)
                        ?: "${it.name} bestel je bij Poketopia. Snel verzonden in Nederland en België.",
                    imageUrl = it.images.firstOrNull() ?: it.card?.shownImageLargeUrl ?: it.card?.shownImageSmallUrl,
                    type = "product",
                )
            },
        )
    }

    /** Only the name, the shop's text and a picture: a drop's price shows only once it is your turn. */
    @Transactional(readOnly = true)
    fun dropPage(slug: String): ResponseEntity<String> {
        val drop = dropRepository.findBySlug(slug)?.takeIf { it.status.public }
        return html(
            drop?.let {
                SharePage(
                    path = path("drops", it.slug),
                    title = it.name,
                    description = it.description?.takeIf(String::isNotBlank) ?: "${it.name}: een drop bij Poketopia.",
                    imageUrl = it.bannerUrl ?: it.products.firstNotNullOfOrNull { product -> product.product.images.firstOrNull() },
                    type = "website",
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun newsPage(slug: String): ResponseEntity<String> {
        val now = clock.instant()
        val article = newsArticleRepository.findBySlug(slug)
            ?.takeIf { it.published && it.publishedAt?.isAfter(now) == false }
        return html(
            article?.let {
                SharePage(
                    path = path("nieuws", it.slug),
                    title = it.title,
                    description = it.summary?.takeIf(String::isNotBlank) ?: it.body,
                    imageUrl = it.imageUrl,
                    type = "article",
                )
            },
        )
    }

    /** A page that doesn't exist still gets the app, which shows its 404 page, with the status a search engine needs. */
    private fun html(page: SharePage?): ResponseEntity<String> {
        val body = seoService.page(page) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.status(if (page == null) HttpStatus.NOT_FOUND else HttpStatus.OK)
            .contentType(MediaType(MediaType.TEXT_HTML, Charsets.UTF_8))
            .cacheControl(CacheControl.noCache())
            .body(body)
    }
}
