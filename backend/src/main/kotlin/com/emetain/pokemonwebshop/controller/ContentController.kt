package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminNewsArticleDto
import com.emetain.pokemonwebshop.dto.AdminNewsletterDto
import com.emetain.pokemonwebshop.dto.InfoPageDto
import com.emetain.pokemonwebshop.dto.InfoPageRequest
import com.emetain.pokemonwebshop.dto.NewsArticleDto
import com.emetain.pokemonwebshop.dto.NewsArticleRequest
import com.emetain.pokemonwebshop.dto.NewsPageDto
import com.emetain.pokemonwebshop.dto.NewsletterSignupRequest
import com.emetain.pokemonwebshop.dto.NewsletterTokenRequest
import com.emetain.pokemonwebshop.orchestration.ContentOrchestrator
import com.emetain.pokemonwebshop.security.AdminOnly
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RateLimited
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/v1")
class ContentController(
    private val contentOrchestrator: ContentOrchestrator,
) {

    @GetMapping("/news")
    fun news(
        @RequestParam(required = false) game: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "12") size: Int,
    ): NewsPageDto = contentOrchestrator.news(game, page, size)

    @GetMapping("/news/{slug}")
    fun article(@PathVariable slug: String, @CurrentUser viewer: User?): NewsArticleDto = contentOrchestrator.article(slug, viewer)

    @GetMapping("/pages/{slug}")
    fun page(@PathVariable slug: String): InfoPageDto = contentOrchestrator.infoPage(slug)

    /** Always the same answer, whether the address was known or not. */
    @PostMapping("/newsletter")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RateLimited("newsletter")
    fun signUp(@Valid @RequestBody request: NewsletterSignupRequest) = contentOrchestrator.signUp(request)

    @PostMapping("/newsletter/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RateLimited("newsletter-link")
    fun confirm(@Valid @RequestBody request: NewsletterTokenRequest) = contentOrchestrator.confirm(request.token)

    @PostMapping("/newsletter/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RateLimited("newsletter-link")
    fun unsubscribe(@Valid @RequestBody request: NewsletterTokenRequest) = contentOrchestrator.unsubscribe(request.token)
}

@RestController
@RequestMapping("/v1/admin")
@AdminOnly
class AdminContentController(
    private val contentOrchestrator: ContentOrchestrator,
) {

    @GetMapping("/news")
    fun news(): List<AdminNewsArticleDto> = contentOrchestrator.adminNews()

    @PostMapping("/news")
    @ResponseStatus(HttpStatus.CREATED)
    fun createArticle(@Valid @RequestBody request: NewsArticleRequest): AdminNewsArticleDto = contentOrchestrator.createArticle(request)

    @PutMapping("/news/{id}")
    fun updateArticle(@PathVariable id: Long, @Valid @RequestBody request: NewsArticleRequest): AdminNewsArticleDto =
        contentOrchestrator.updateArticle(id, request)

    @DeleteMapping("/news/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteArticle(@PathVariable id: Long) = contentOrchestrator.deleteArticle(id)

    @PostMapping("/news/{id}/image")
    fun replaceArticleImage(@PathVariable id: Long, @RequestParam file: MultipartFile): AdminNewsArticleDto =
        contentOrchestrator.replaceArticleImage(id, file.bytes)

    @DeleteMapping("/news/{id}/image")
    fun removeArticleImage(@PathVariable id: Long): AdminNewsArticleDto = contentOrchestrator.removeArticleImage(id)

    @GetMapping("/pages")
    fun pages(): List<InfoPageDto> = contentOrchestrator.adminPages()

    @PutMapping("/pages/{slug}")
    fun updatePage(@PathVariable slug: String, @Valid @RequestBody request: InfoPageRequest): InfoPageDto =
        contentOrchestrator.updatePage(slug, request)

    @PostMapping("/pages/{slug}/reset")
    fun resetPage(@PathVariable slug: String): InfoPageDto = contentOrchestrator.resetPage(slug)

    @GetMapping("/newsletter")
    fun subscribers(@RequestParam(required = false) status: String?, @RequestParam(defaultValue = "0") page: Int): AdminNewsletterDto =
        contentOrchestrator.subscribers(status, page)

    @GetMapping("/newsletter/export")
    fun export(): ResponseEntity<String> = ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nieuwsbrief.csv\"")
        .contentType(MediaType("text", "csv", Charsets.UTF_8))
        .body(contentOrchestrator.exportSubscribers())

    @DeleteMapping("/newsletter/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSubscriber(@PathVariable id: Long) = contentOrchestrator.deleteSubscriber(id)
}
