package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.NewsletterStatus
import com.emetain.pokemonwebshop.domain.NewsletterSubscriber
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.NewsletterSubscriberRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Locale

/** A signup that needs a confirmation mail: the links go in the mail, only their hashes are stored. */
data class NewsletterMail(val email: String, val confirmToken: String, val unsubscribeToken: String)

/**
 * The newsletter list with double opt-in: a signup gets a mail with a confirmation link, and only confirmed
 * addresses go in the export. The answer to a signup is the same whether the address was known or not.
 */
@Service
class NewsletterService(
    private val subscriberRepository: NewsletterSubscriberRepository,
    private val tokenService: TokenService,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Null when nothing has to be sent: the address is confirmed already. */
    @Transactional
    fun signUp(rawEmail: String): NewsletterMail? {
        val email = rawEmail.trim().lowercase(Locale.ROOT)
        if (!EMAIL.matches(email) || email.length > MAX_EMAIL) throw ApiException.validation("Vul een geldig e-mailadres in.")
        val now = clock.instant()
        val subscriber = subscriberRepository.findByEmail(email)
            ?: try {
                subscriberRepository.saveAndFlush(NewsletterSubscriber(email = email, createdAt = now))
            } catch (exception: DataIntegrityViolationException) {
                // Someone signed up with the same address at the same moment.
                return null
            }
        if (subscriber.status == NewsletterStatus.CONFIRMED) return null
        if (subscriber.status == NewsletterStatus.UNSUBSCRIBED) {
            subscriber.status = NewsletterStatus.PENDING
            subscriber.createdAt = now
        }
        val confirm = tokenService.generate()
        val unsubscribe = tokenService.generate()
        subscriber.confirmTokenHash = tokenService.hash(confirm)
        subscriber.confirmTokenExpiresAt = now.plus(properties.newsletter.confirmTtl)
        subscriber.unsubscribeTokenHash = tokenService.hash(unsubscribe)
        return NewsletterMail(email, confirm, unsubscribe)
    }

    @Transactional
    fun confirm(token: String) {
        val subscriber = subscriberRepository.findByConfirmTokenHash(tokenService.hash(token.trim()))
        val expiresAt = subscriber?.confirmTokenExpiresAt
        if (subscriber == null || expiresAt == null || !clock.instant().isBefore(expiresAt)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "invalid_token", "Deze link werkt niet meer. Meld je opnieuw aan, dan krijg je een nieuwe mail.")
        }
        subscriber.status = NewsletterStatus.CONFIRMED
        subscriber.confirmedAt = clock.instant()
        subscriber.unsubscribedAt = null
        subscriber.confirmTokenHash = null
        subscriber.confirmTokenExpiresAt = null
    }

    /** Works any number of times, so an old link from a mail never shows an error. */
    @Transactional
    fun unsubscribe(token: String) {
        val subscriber = subscriberRepository.findByUnsubscribeTokenHash(tokenService.hash(token.trim()))
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "invalid_token", "We konden deze aanmelding niet vinden. Misschien ben je al afgemeld.")
        if (subscriber.status != NewsletterStatus.UNSUBSCRIBED) {
            subscriber.status = NewsletterStatus.UNSUBSCRIBED
            subscriber.unsubscribedAt = clock.instant()
            subscriber.confirmTokenHash = null
            subscriber.confirmTokenExpiresAt = null
        }
    }

    @Transactional(readOnly = true)
    fun counts(): Map<NewsletterStatus, Long> = NewsletterStatus.entries.associateWith(subscriberRepository::countByStatus)

    @Transactional(readOnly = true)
    fun forAdmin(status: NewsletterStatus?, page: Int): Page<NewsletterSubscriber> =
        subscriberRepository.findForAdmin(status, PageRequest.of(page.coerceAtLeast(0), ADMIN_PAGE_SIZE))

    @Transactional(readOnly = true)
    fun confirmed(): List<NewsletterSubscriber> = subscriberRepository.findByStatusOrderByConfirmedAtAsc(NewsletterStatus.CONFIRMED)

    @Transactional
    fun delete(subscriberId: Long) = subscriberRepository.deleteById(subscriberId)

    /** Unconfirmed signups don't stay: after 30 days they are removed. */
    @Scheduled(cron = "\${app.newsletter.cleanup-cron}", zone = "Europe/Amsterdam")
    @Transactional
    fun removeStale() {
        val removed = subscriberRepository.deleteStale(clock.instant().minus(properties.newsletter.pendingTtl))
        if (removed > 0) log.info("Removed {} newsletter signups that were never confirmed", removed)
    }

    private companion object {
        const val MAX_EMAIL = 254
        const val ADMIN_PAGE_SIZE = 50
        val EMAIL = Regex("[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}")
    }
}
