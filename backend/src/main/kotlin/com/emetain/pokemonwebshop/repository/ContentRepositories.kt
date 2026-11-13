package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.InfoPage
import com.emetain.pokemonwebshop.domain.NewsArticle
import com.emetain.pokemonwebshop.domain.NewsletterStatus
import com.emetain.pokemonwebshop.domain.NewsletterSubscriber
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface NewsArticleRepository : JpaRepository<NewsArticle, Long> {

    @Query(
        """
        select a from NewsArticle a
        where a.published = true and a.publishedAt <= :now and (:game is null or a.game = :game)
        order by a.publishedAt desc, a.id desc
        """,
    )
    fun findPublished(now: Instant, game: Game?, pageable: Pageable): Page<NewsArticle>

    fun findBySlug(slug: String): NewsArticle?

    fun existsBySlug(slug: String): Boolean

    @Query("select a from NewsArticle a order by coalesce(a.publishedAt, a.createdAt) desc, a.id desc")
    fun findAllForAdmin(): List<NewsArticle>
}

interface InfoPageRepository : JpaRepository<InfoPage, Long> {
    fun findBySlug(slug: String): InfoPage?

    fun findAllByOrderBySlug(): List<InfoPage>
}

interface NewsletterSubscriberRepository : JpaRepository<NewsletterSubscriber, Long> {
    fun findByEmail(email: String): NewsletterSubscriber?

    fun findByConfirmTokenHash(hash: String): NewsletterSubscriber?

    fun findByUnsubscribeTokenHash(hash: String): NewsletterSubscriber?

    fun countByStatus(status: NewsletterStatus): Long

    fun findByStatusOrderByConfirmedAtAsc(status: NewsletterStatus): List<NewsletterSubscriber>

    @Query("select s from NewsletterSubscriber s where (:status is null or s.status = :status) order by s.createdAt desc, s.id desc")
    fun findForAdmin(status: NewsletterStatus?, pageable: Pageable): Page<NewsletterSubscriber>

    @Modifying
    @Query("delete from NewsletterSubscriber s where s.status = :status and s.createdAt < :before")
    fun deleteStale(before: Instant, status: NewsletterStatus = NewsletterStatus.PENDING): Int
}
