package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.NewsArticle
import com.emetain.pokemonwebshop.dto.NewsArticleRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.NewsArticleRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class NewsService(
    private val articleRepository: NewsArticleRepository,
    private val mediaStorage: MediaStorage,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun published(game: Game?, page: Int, size: Int): Page<NewsArticle> =
        articleRepository.findPublished(clock.instant(), game, PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE)))

    /** A published article, or for an admin also a draft, as a preview. */
    @Transactional(readOnly = true)
    fun bySlug(slug: String, admin: Boolean): NewsArticle {
        val article = articleRepository.findBySlug(slug) ?: throw notFound()
        val visible = article.published && article.publishedAt?.let { !it.isAfter(clock.instant()) } == true
        if (!visible && !admin) throw notFound()
        return article
    }

    @Transactional(readOnly = true)
    fun all(): List<NewsArticle> = articleRepository.findAllForAdmin()

    @Transactional
    fun create(request: NewsArticleRequest): NewsArticle {
        val now = clock.instant()
        val title = request.title.trim()
        val article = NewsArticle(
            title = title,
            slug = ProductSlugs.unique(title, articleRepository::existsBySlug),
            body = "",
            createdAt = now,
            updatedAt = now,
        )
        apply(article, request)
        return articleRepository.save(article)
    }

    @Transactional
    fun update(articleId: Long, request: NewsArticleRequest): NewsArticle = find(articleId).also {
        apply(it, request)
        it.updatedAt = clock.instant()
    }

    @Transactional
    fun delete(articleId: Long) {
        val article = find(articleId)
        article.imageUrl?.let(mediaStorage::delete)
        articleRepository.delete(article)
    }

    @Transactional
    fun replaceImage(articleId: Long, bytes: ByteArray): NewsArticle {
        val article = find(articleId)
        val format = ImageFormat.detect(bytes) ?: throw ApiException.validation("Kies een afbeelding in JPG, PNG of WebP.")
        val previous = article.imageUrl
        article.imageUrl = mediaStorage.store(bytes, format)
        previous?.let(mediaStorage::delete)
        return article
    }

    @Transactional
    fun removeImage(articleId: Long): NewsArticle = find(articleId).also { article ->
        article.imageUrl?.let(mediaStorage::delete)
        article.imageUrl = null
    }

    private fun apply(article: NewsArticle, request: NewsArticleRequest) {
        val title = request.title.trim()
        if (title.isEmpty()) throw ApiException.validation("Vul een titel in.")
        val body = request.body.trim()
        if (body.isEmpty()) throw ApiException.validation("Schrijf de tekst van het bericht.")
        article.title = title
        article.body = body
        article.summary = request.summary?.trim()?.takeIf { it.isNotEmpty() }
        article.game = request.game?.takeIf { it.isNotBlank() }?.let { Game.fromSlug(it) ?: throw ApiException.validation("Kies een geldig spel.") }
        article.published = request.published
        // Publishing without a date publishes now. A date in the future plans the article.
        article.publishedAt = when {
            request.publishedAt != null -> request.publishedAt
            request.published -> article.publishedAt ?: clock.instant()
            else -> article.publishedAt
        }
    }

    private fun find(articleId: Long) = articleRepository.findById(articleId).orElseThrow { notFound() }

    private fun notFound() = ApiException.notFound("We konden dit bericht niet vinden.")

    private companion object {
        const val MAX_PAGE_SIZE = 24
    }
}
