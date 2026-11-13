package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.NewsletterStatus
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminNewsArticleDto
import com.emetain.pokemonwebshop.dto.AdminNewsletterDto
import com.emetain.pokemonwebshop.dto.AdminSubscriberDto
import com.emetain.pokemonwebshop.dto.InfoPageDto
import com.emetain.pokemonwebshop.dto.InfoPageRequest
import com.emetain.pokemonwebshop.dto.NewsArticleDto
import com.emetain.pokemonwebshop.dto.NewsArticleRequest
import com.emetain.pokemonwebshop.dto.NewsPageDto
import com.emetain.pokemonwebshop.dto.NewsSummaryDto
import com.emetain.pokemonwebshop.dto.NewsletterSignupRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.service.InfoPageService
import com.emetain.pokemonwebshop.service.MailService
import com.emetain.pokemonwebshop.service.NewsService
import com.emetain.pokemonwebshop.service.NewsletterService
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** News, the info pages, the newsletter and the contact form. */
@Component
class ContentOrchestrator(
    private val newsService: NewsService,
    private val infoPageService: InfoPageService,
    private val newsletterService: NewsletterService,
    private val mailService: MailService,
) {

    fun news(gameSlug: String?, page: Int, size: Int): NewsPageDto {
        val game = gameSlug?.takeIf { it.isNotBlank() }?.let { Game.fromSlug(it) ?: throw ApiException.notFound() }
        val result = newsService.published(game, page, size)
        return NewsPageDto(result.content.map(NewsSummaryDto::from), result.number, result.totalPages)
    }

    fun article(slug: String, viewer: User?): NewsArticleDto =
        NewsArticleDto.from(newsService.bySlug(slug, admin = viewer?.role == Role.ADMIN))

    fun infoPage(slug: String): InfoPageDto = InfoPageDto.from(infoPageService.bySlug(slug))

    fun signUp(request: NewsletterSignupRequest) {
        checkHoneypot(request, "newsletter signup")
        newsletterService.signUp(request.email)?.let {
            mailService.sendNewsletterConfirmation(it.email, it.confirmToken, it.unsubscribeToken)
        }
    }

    fun confirm(token: String) = newsletterService.confirm(token)

    fun unsubscribe(token: String) = newsletterService.unsubscribe(token)

    fun adminNews(): List<AdminNewsArticleDto> = newsService.all().map(AdminNewsArticleDto::from)

    fun createArticle(request: NewsArticleRequest) = AdminNewsArticleDto.from(newsService.create(request))

    fun updateArticle(id: Long, request: NewsArticleRequest) = AdminNewsArticleDto.from(newsService.update(id, request))

    fun deleteArticle(id: Long) = newsService.delete(id)

    fun replaceArticleImage(id: Long, bytes: ByteArray) = AdminNewsArticleDto.from(newsService.replaceImage(id, bytes))

    fun removeArticleImage(id: Long) = AdminNewsArticleDto.from(newsService.removeImage(id))

    fun adminPages(): List<InfoPageDto> = infoPageService.all().map(InfoPageDto::from)

    fun updatePage(slug: String, request: InfoPageRequest) = InfoPageDto.from(infoPageService.update(slug, request))

    fun resetPage(slug: String) = InfoPageDto.from(infoPageService.reset(slug))

    fun subscribers(status: String?, page: Int): AdminNewsletterDto {
        val wanted = status?.takeIf { it.isNotBlank() }?.let {
            runCatching { NewsletterStatus.valueOf(it) }.getOrElse { throw ApiException.validation("Kies een geldige status.") }
        }
        val result = newsletterService.forAdmin(wanted, page)
        return AdminNewsletterDto(
            subscribers = result.content.map(AdminSubscriberDto::from),
            page = result.number,
            totalPages = result.totalPages,
            counts = newsletterService.counts().mapKeys { it.key.name },
        )
    }

    /** Confirmed addresses only, for a mail program. Semicolons, so Dutch Excel opens it in columns. */
    fun exportSubscribers(): String = buildString {
        append('﻿')
        append("email;aangemeld_op;bevestigd_op\r\n")
        for (subscriber in newsletterService.confirmed()) {
            append(subscriber.email).append(';')
            append(DATE_TIME.format(subscriber.createdAt.atZone(ZONE))).append(';')
            append(subscriber.confirmedAt?.let { DATE_TIME.format(it.atZone(ZONE)) }.orEmpty()).append("\r\n")
        }
    }

    fun deleteSubscriber(id: Long) = newsletterService.delete(id)

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
