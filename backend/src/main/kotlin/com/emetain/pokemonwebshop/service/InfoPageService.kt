package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.InfoPage
import com.emetain.pokemonwebshop.dto.InfoPageRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.InfoPageRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** The pages under /info. Every page in [InfoPageDefaults] exists, with its starting text until the admin edits it. */
@Service
class InfoPageService(
    private val pageRepository: InfoPageRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Creates the pages that don't exist yet. Never touches a page that is there. */
    @EventListener(ApplicationReadyEvent::class)
    fun createMissing() {
        val existing = pageRepository.findAll().map { it.slug }.toSet()
        val missing = InfoPageDefaults.PAGES.filter { it.slug !in existing }
        for (page in missing) {
            try {
                pageRepository.save(InfoPage(slug = page.slug, title = page.title, body = page.body, updatedAt = clock.instant(), draft = page.draft))
            } catch (exception: DataIntegrityViolationException) {
                // Another instance made it at the same moment.
            }
        }
        if (missing.isNotEmpty()) log.info("Created {} info pages with their starting text", missing.size)
    }

    @Transactional(readOnly = true)
    fun bySlug(slug: String): InfoPage =
        pageRepository.findBySlug(slug) ?: throw ApiException.notFound("We konden deze pagina niet vinden.")

    @Transactional(readOnly = true)
    fun all(): List<InfoPage> = pageRepository.findAllByOrderBySlug()

    @Transactional
    fun update(slug: String, request: InfoPageRequest): InfoPage {
        val page = pageRepository.findBySlug(slug) ?: throw ApiException.notFound("We konden deze pagina niet vinden.")
        val title = request.title.trim()
        val body = request.body.trim()
        if (title.isEmpty()) throw ApiException.validation("Vul een titel in.")
        if (body.isEmpty()) throw ApiException.validation("Een pagina kan niet leeg zijn.")
        page.title = title
        page.body = body
        page.draft = request.draft
        page.updatedAt = clock.instant()
        return page
    }

    /** Puts the starting text back, for an admin who wants to start over. */
    @Transactional
    fun reset(slug: String): InfoPage {
        val page = pageRepository.findBySlug(slug) ?: throw ApiException.notFound("We konden deze pagina niet vinden.")
        val default = InfoPageDefaults.PAGES.first { it.slug == slug }
        page.title = default.title
        page.body = default.body
        page.draft = default.draft
        page.updatedAt = clock.instant()
        return page
    }
}
