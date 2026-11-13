package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.SupportMessage
import com.emetain.pokemonwebshop.domain.SupportTicket
import com.emetain.pokemonwebshop.domain.TicketStatus
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.ChatTurn
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.SupportTicketRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.util.Locale

/** Short, random ticket numbers without letters that look alike, such as `T-7K3M9Q`. */
object TicketNumbers {
    private const val PREFIX = "T-"
    private const val LENGTH = 6
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private val random = SecureRandom()

    fun generate(): String = PREFIX + (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    fun normalize(input: String): String = input.trim().uppercase(Locale.ROOT)
}

/** The checks on what a customer writes. Pure, so they can be tested without a database. */
object TicketRules {
    const val MIN_MESSAGE = 10
    const val MIN_REPLY = 2
    const val MAX_BODY = 10_000
    private const val MAX_CHAT_TEXT = 4_000
    private val EMAIL = Regex("[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}")
    private val LINE_BREAK = Regex("[\\r\\n]+")

    fun validEmail(email: String): Boolean = EMAIL.matches(email)

    /** One line: a subject ends up in a mail header. */
    fun oneLine(text: String): String = text.trim().replace(LINE_BREAK, " ")

    /** The message, with the conversation with the chatbot below it when the customer added it. */
    fun body(message: String, chat: List<ChatTurn>): String {
        val turns = chat.filter { it.text.isNotBlank() && it.role in setOf("user", "assistant") }
        if (turns.isEmpty()) return message
        val conversation = turns.joinToString("\n") { (if (it.role == "user") "Klant: " else "Assistent: ") + it.text.trim() }
            .take(MAX_CHAT_TEXT)
        return "$message\n\n--- Gesprek met de assistent ---\n$conversation".take(MAX_BODY)
    }
}

/**
 * Customer service tickets. A customer opens one from the contact page, logged in or not, and follows it with the
 * secret link in the confirmation mail. The shop answers in admin. Mails go out from the orchestrator, after the
 * transaction: a mail never goes out for something that wasn't saved.
 */
@Service
class SupportTicketService(
    private val ticketRepository: SupportTicketRepository,
    private val tokenService: TokenService,
    private val clock: Clock,
) {

    @Transactional
    fun create(viewer: User?, name: String, email: String, subject: String, orderNumber: String?, body: String): SupportTicket {
        val cleanName = TicketRules.oneLine(name).ifEmpty { viewer?.displayName.orEmpty() }.take(NAME_LENGTH)
        val cleanEmail = (viewer?.email ?: email).trim().lowercase(Locale.ROOT)
        val cleanSubject = TicketRules.oneLine(subject).take(SUBJECT_LENGTH)
        val cleanBody = body.trim()
        if (cleanName.isEmpty()) throw ApiException.validation("Vul je naam in.")
        if (!TicketRules.validEmail(cleanEmail)) throw ApiException.validation("Vul een geldig e-mailadres in.")
        if (cleanSubject.isEmpty()) throw ApiException.validation("Vul een onderwerp in.")
        if (cleanBody.length < TicketRules.MIN_MESSAGE) {
            throw ApiException.validation("Schrijf een bericht van minstens ${TicketRules.MIN_MESSAGE} tekens.")
        }
        val now = clock.instant()
        val ticket = SupportTicket(
            number = generateSequence { TicketNumbers.generate() }.first { !ticketRepository.existsByNumber(it) },
            accessToken = tokenService.generate(),
            name = cleanName,
            email = cleanEmail,
            subject = cleanSubject,
            createdAt = now,
            updatedAt = now,
            user = viewer,
            orderNumber = orderNumber?.let(OrderNumbers::normalize)?.takeIf { it.isNotEmpty() }?.take(ORDER_LENGTH),
        )
        ticket.messages += SupportMessage(ticket, fromShop = false, body = cleanBody.take(TicketRules.MAX_BODY), createdAt = now)
        return ticketRepository.save(ticket)
    }

    @Transactional(readOnly = true)
    fun byToken(token: String): SupportTicket = ticketRepository.findByAccessToken(token.trim()) ?: throw notFound()

    @Transactional(readOnly = true)
    fun byNumber(number: String): SupportTicket = ticketRepository.findByNumber(TicketNumbers.normalize(number)) ?: throw notFound()

    @Transactional(readOnly = true)
    fun forUser(userId: Long): List<SupportTicket> = ticketRepository.findByUserIdOrderByUpdatedAtDesc(userId)

    @Transactional(readOnly = true)
    fun forAdmin(status: TicketStatus?, page: Int): Page<SupportTicket> =
        ticketRepository.findForAdmin(status, PageRequest.of(page.coerceAtLeast(0), PAGE_SIZE))

    @Transactional(readOnly = true)
    fun counts(): Map<TicketStatus, Long> = TicketStatus.entries.associateWith(ticketRepository::countByStatus)

    /** The customer writes again. A closed ticket opens again: it is waiting for the shop. */
    @Transactional
    fun customerReply(token: String, message: String): SupportTicket {
        val ticket = byToken(token)
        val body = message.trim()
        if (body.length < TicketRules.MIN_REPLY) throw ApiException.validation("Schrijf eerst een bericht.")
        val now = clock.instant()
        ticket.messages += SupportMessage(ticket, fromShop = false, body = body.take(TicketRules.MAX_BODY), createdAt = now)
        ticket.status = TicketStatus.OPEN
        ticket.updatedAt = now
        return ticket
    }

    @Transactional
    fun shopReply(number: String, admin: User, message: String, close: Boolean): SupportTicket {
        val ticket = byNumber(number)
        val body = message.trim()
        if (body.length < TicketRules.MIN_REPLY) throw ApiException.validation("Schrijf eerst een antwoord.")
        val now = clock.instant()
        ticket.messages += SupportMessage(ticket, fromShop = true, body = body.take(TicketRules.MAX_BODY), createdAt = now, author = admin)
        ticket.status = if (close) TicketStatus.CLOSED else TicketStatus.ANSWERED
        ticket.updatedAt = now
        return ticket
    }

    /** Close a ticket that needs no answer, or open it again. */
    @Transactional
    fun setStatus(number: String, status: String): SupportTicket {
        val wanted = when (status.trim().uppercase(Locale.ROOT)) {
            "OPEN" -> TicketStatus.OPEN
            "CLOSED" -> TicketStatus.CLOSED
            else -> throw ApiException.validation("Kies open of gesloten.")
        }
        val ticket = byNumber(number)
        ticket.status = wanted
        ticket.updatedAt = clock.instant()
        return ticket
    }

    private fun notFound() = ApiException.notFound("We konden dit ticket niet vinden.")

    private companion object {
        const val NAME_LENGTH = 100
        const val SUBJECT_LENGTH = 150
        const val ORDER_LENGTH = 20
        const val PAGE_SIZE = 25
    }
}
