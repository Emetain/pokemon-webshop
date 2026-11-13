package com.emetain.pokemonwebshop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * A news article, managed in admin and shown on /nieuws and the home page. [body] is plain text with a few marks:
 * `## ` headings, `- ` lists, `**bold**` and `[text](link)`. The site turns those into HTML itself, so no HTML from
 * the admin ever reaches a page.
 */
@Entity
@Table(
    name = "news_articles",
    uniqueConstraints = [UniqueConstraint(name = "uk_news_articles_slug", columnNames = ["slug"])],
    indexes = [Index(name = "idx_news_articles_published", columnList = "published, published_at")],
)
class NewsArticle(
    @Column(nullable = false, length = 150)
    var title: String,

    @Column(nullable = false, length = 180)
    var slug: String,

    @Column(nullable = false, length = 20000)
    var body: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,

    /** The short text in lists and on the home page. */
    @Column(length = 300)
    var summary: String? = null,

    @Column(length = 500)
    var imageUrl: String? = null,

    /** Null for news about the shop itself. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var game: Game? = null,

    /** Shows from this moment. Set when the admin publishes, and can be set later to plan an article. */
    @Column
    var publishedAt: Instant? = null,

    @Column(nullable = false)
    var published: Boolean = false,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)

/**
 * A page under /info, such as shipping, returns, terms and privacy. The shop creates each one with a starting text
 * at startup, and the admin edits it. [draft] marks a text that must be checked before the shop opens: the legal
 * pages start as drafts. Same marks in [body] as a news article.
 */
@Entity
@Table(
    name = "info_pages",
    uniqueConstraints = [UniqueConstraint(name = "uk_info_pages_slug", columnNames = ["slug"])],
)
class InfoPage(
    @Column(nullable = false, length = 60)
    val slug: String,

    @Column(nullable = false, length = 150)
    var title: String,

    @Column(nullable = false, length = 50000)
    var body: String,

    @Column(nullable = false)
    var updatedAt: Instant,

    @Column(nullable = false)
    var draft: Boolean = true,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)

enum class NewsletterStatus { PENDING, CONFIRMED, UNSUBSCRIBED }

/**
 * Someone who signed up for the newsletter. Double opt-in: only [NewsletterStatus.CONFIRMED] after clicking the link
 * in the mail. Only hashes of the links are stored. Unconfirmed signups are removed after 30 days.
 */
@Entity
@Table(
    name = "newsletter_subscribers",
    uniqueConstraints = [UniqueConstraint(name = "uk_newsletter_subscribers_email", columnNames = ["email"])],
    indexes = [
        Index(name = "idx_newsletter_subscribers_confirm", columnList = "confirm_token_hash"),
        Index(name = "idx_newsletter_subscribers_unsubscribe", columnList = "unsubscribe_token_hash"),
        Index(name = "idx_newsletter_subscribers_status", columnList = "status, created_at"),
    ],
)
class NewsletterSubscriber(
    @Column(nullable = false, length = 254)
    val email: String,

    @Column(nullable = false)
    var createdAt: Instant,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: NewsletterStatus = NewsletterStatus.PENDING,

    @Column(length = 64)
    var confirmTokenHash: String? = null,

    @Column
    var confirmTokenExpiresAt: Instant? = null,

    @Column(length = 64)
    var unsubscribeTokenHash: String? = null,

    @Column
    var confirmedAt: Instant? = null,

    @Column
    var unsubscribedAt: Instant? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
