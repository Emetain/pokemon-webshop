package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.InfoPage
import com.emetain.pokemonwebshop.domain.NewsArticle
import com.emetain.pokemonwebshop.domain.NewsletterSubscriber
import jakarta.validation.constraints.Size
import java.time.Instant

data class NewsSummaryDto(
    val slug: String,
    val title: String,
    val summary: String?,
    val imageUrl: String?,
    val game: String?,
    val publishedAt: Instant?,
) {
    companion object {
        fun from(article: NewsArticle) =
            NewsSummaryDto(article.slug, article.title, article.summary, article.imageUrl, article.game?.slug, article.publishedAt)
    }
}

data class NewsPageDto(val articles: List<NewsSummaryDto>, val page: Int, val totalPages: Int)

data class NewsArticleDto(
    val slug: String,
    val title: String,
    val summary: String?,
    val body: String,
    val imageUrl: String?,
    val game: String?,
    val publishedAt: Instant?,
    /** False for a draft an admin previews. */
    val published: Boolean,
) {
    companion object {
        fun from(article: NewsArticle) = NewsArticleDto(
            article.slug, article.title, article.summary, article.body, article.imageUrl, article.game?.slug, article.publishedAt, article.published,
        )
    }
}

data class AdminNewsArticleDto(
    val id: Long,
    val slug: String,
    val title: String,
    val summary: String?,
    val body: String,
    val imageUrl: String?,
    val game: String?,
    val published: Boolean,
    val publishedAt: Instant?,
    val updatedAt: Instant,
) {
    companion object {
        fun from(article: NewsArticle) = AdminNewsArticleDto(
            article.id, article.slug, article.title, article.summary, article.body, article.imageUrl, article.game?.slug,
            article.published, article.publishedAt, article.updatedAt,
        )
    }
}

data class NewsArticleRequest(
    @field:Size(max = 150, message = "De titel mag maximaal 150 tekens hebben.")
    val title: String = "",
    @field:Size(max = 300, message = "De samenvatting mag maximaal 300 tekens hebben.")
    val summary: String? = null,
    @field:Size(max = 20000, message = "De tekst mag maximaal 20.000 tekens hebben.")
    val body: String = "",
    val game: String? = null,
    val published: Boolean = false,
    /** Empty publishes now. A moment in the future plans the article. */
    val publishedAt: Instant? = null,
)

data class InfoPageDto(val slug: String, val title: String, val body: String, val draft: Boolean, val updatedAt: Instant) {
    companion object {
        fun from(page: InfoPage) = InfoPageDto(page.slug, page.title, page.body, page.draft, page.updatedAt)
    }
}

data class InfoPageRequest(
    @field:Size(max = 150, message = "De titel mag maximaal 150 tekens hebben.")
    val title: String = "",
    @field:Size(max = 50000, message = "De tekst mag maximaal 50.000 tekens hebben.")
    val body: String = "",
    val draft: Boolean = false,
)

data class NewsletterSignupRequest(
    @field:Size(max = 254, message = "Dit e-mailadres is te lang.")
    val email: String = "",
    override val website: String? = null,
) : HoneypotRequest

data class NewsletterTokenRequest(
    @field:Size(max = 100, message = "Deze link klopt niet.")
    val token: String = "",
)

data class AdminSubscriberDto(
    val id: Long,
    val email: String,
    val status: String,
    val createdAt: Instant,
    val confirmedAt: Instant?,
    val unsubscribedAt: Instant?,
) {
    companion object {
        fun from(subscriber: NewsletterSubscriber) = AdminSubscriberDto(
            subscriber.id, subscriber.email, subscriber.status.name, subscriber.createdAt, subscriber.confirmedAt, subscriber.unsubscribedAt,
        )
    }
}

data class AdminNewsletterDto(
    val subscribers: List<AdminSubscriberDto>,
    val page: Int,
    val totalPages: Int,
    /** Per status: `PENDING`, `CONFIRMED`, `UNSUBSCRIBED`. */
    val counts: Map<String, Long>,
)
