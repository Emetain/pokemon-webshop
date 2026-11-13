package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.SupportTicket
import com.emetain.pokemonwebshop.domain.TicketStatus
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminTicketDto
import com.emetain.pokemonwebshop.dto.AdminTicketPageDto
import com.emetain.pokemonwebshop.dto.AdminTicketReplyRequest
import com.emetain.pokemonwebshop.dto.AdminTicketSummaryDto
import com.emetain.pokemonwebshop.dto.ChatAnswerDto
import com.emetain.pokemonwebshop.dto.ChatRequest
import com.emetain.pokemonwebshop.dto.ChatStatusDto
import com.emetain.pokemonwebshop.dto.CreateTicketRequest
import com.emetain.pokemonwebshop.dto.TicketDto
import com.emetain.pokemonwebshop.dto.TicketReplyRequest
import com.emetain.pokemonwebshop.dto.TicketStatusRequest
import com.emetain.pokemonwebshop.dto.TicketSummaryDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.service.MailService
import com.emetain.pokemonwebshop.service.SupportTicketService
import com.emetain.pokemonwebshop.service.TicketRules
import com.emetain.pokemonwebshop.service.ai.SupportChatService
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Customer service: the chatbot, tickets for customers, and answering them in admin. Mails go out here, after the
 * service saved the change.
 *
 * The confirmation to the customer has no text the visitor typed: the form takes any address, so a mail with the
 * visitor's own words would let anyone send their text to anyone through the shop.
 */
@Component
class SupportOrchestrator(
    private val ticketService: SupportTicketService,
    private val chatService: SupportChatService,
    private val mailService: MailService,
    private val properties: AppProperties,
    private val clock: Clock,
) {

    fun chatStatus() = ChatStatusDto(chatService.available())

    fun chat(request: ChatRequest, viewer: User?): ChatAnswerDto {
        checkHoneypot(request, "chatbot")
        return chatService.answer(request.messages, viewer)
    }

    fun create(request: CreateTicketRequest, viewer: User?): TicketDto {
        checkHoneypot(request, "ticket")
        val message = request.message.trim()
        if (message.length < TicketRules.MIN_MESSAGE) {
            throw ApiException.validation("Schrijf een bericht van minstens ${TicketRules.MIN_MESSAGE} tekens.")
        }
        val ticket = ticketService.create(
            viewer = viewer,
            name = request.name,
            email = request.email,
            subject = request.subject,
            orderNumber = request.orderNumber,
            body = TicketRules.body(message, request.chat),
        )
        mailShop(ticket, "Nieuw ticket ${ticket.number}: ${ticket.subject}", "Nieuw ticket via de klantenservice van Poketopia")
        mailService.sendSupportMail(
            to = ticket.email,
            subject = "We hebben je vraag ontvangen (ticket ${ticket.number})",
            text = """
                Hoi,

                Bedankt voor je bericht aan Poketopia. We hebben je vraag ontvangen als ticket ${ticket.number}, en we kijken er zo snel mogelijk naar. Ons antwoord krijg je per mail.

                Je ticket bekijken of iets aanvullen kan via deze link:
                ${customerLink(ticket)}

                Deel deze link niet: met de link kan iemand je ticket lezen.

                Heb je deze vraag niet zelf gestuurd? Dan kun je deze mail negeren.

                Groeten,
                Poketopia
            """.trimIndent(),
            replyTo = mailService.shopAddress,
        )
        return TicketDto.from(ticket)
    }

    fun ticket(token: String): TicketDto = TicketDto.from(ticketService.byToken(token))

    fun reply(token: String, request: TicketReplyRequest): TicketDto {
        val ticket = ticketService.customerReply(token, request.message)
        mailShop(ticket, "Nieuw bericht bij ticket ${ticket.number}: ${ticket.subject}", "Nieuw bericht van de klant bij ticket ${ticket.number}")
        return TicketDto.from(ticket)
    }

    fun mine(viewer: User): List<TicketSummaryDto> = ticketService.forUser(viewer.id).map(TicketSummaryDto::from)

    fun adminList(status: String?, page: Int): AdminTicketPageDto {
        val wanted = status?.takeIf { it.isNotBlank() }?.let {
            runCatching { TicketStatus.valueOf(it.uppercase(Locale.ROOT)) }.getOrElse { throw ApiException.validation("Kies een geldige status.") }
        }
        val result = ticketService.forAdmin(wanted, page)
        return AdminTicketPageDto(
            tickets = result.content.map(AdminTicketSummaryDto::from),
            page = result.number,
            totalPages = result.totalPages,
            counts = ticketService.counts().mapKeys { it.key.name },
        )
    }

    fun adminTicket(number: String): AdminTicketDto = AdminTicketDto.from(ticketService.byNumber(number))

    fun adminReply(number: String, admin: User, request: AdminTicketReplyRequest): AdminTicketDto {
        val ticket = ticketService.shopReply(number, admin, request.message, request.close)
        val answer = ticket.messages.last().body
        val closed = if (ticket.status == TicketStatus.CLOSED) {
            "\n\nWe hebben het ticket hiermee gesloten. Reageer je via de link, dan gaat het weer open."
        } else {
            ""
        }
        mailService.sendSupportMail(
            to = ticket.email,
            subject = "Antwoord op je vraag (ticket ${ticket.number})",
            text = "Hoi,\n\nWe hebben je vraag \"${ticket.subject}\" beantwoord:\n\n$answer\n\n" +
                "Het hele gesprek staat bij je ticket. Reageren kan via deze link, of door deze mail te beantwoorden:\n" +
                "${customerLink(ticket)}$closed\n\nGroeten,\nPoketopia",
            replyTo = mailService.shopAddress,
        )
        return AdminTicketDto.from(ticket)
    }

    fun adminStatus(number: String, request: TicketStatusRequest): AdminTicketDto =
        AdminTicketDto.from(ticketService.setStatus(number, request.status))

    /** To the shop's mailbox, with the latest message and the link to answer it in admin. Answering the mail reaches the customer. */
    private fun mailShop(ticket: SupportTicket, subject: String, heading: String) {
        val text = buildString {
            appendLine("$heading, ${DATE_TIME.format(clock.instant().atZone(ZONE))}.")
            appendLine()
            appendLine("Naam: ${ticket.name}")
            appendLine("E-mail: ${ticket.email}")
            appendLine("Account: ${if (ticket.user != null) "ja" else "nee, als gast"}")
            ticket.orderNumber?.let { appendLine("Bestelnummer: $it") }
            appendLine("Onderwerp: ${ticket.subject}")
            appendLine()
            appendLine(ticket.messages.last().body)
            appendLine()
            append("Beantwoorden in admin, dan blijft alles bij het ticket: ${properties.baseUrl}/admin/tickets/${ticket.number}")
        }
        mailService.sendSupportMail(mailService.shopAddress, subject.take(MAX_SUBJECT), text, replyTo = ticket.email)
    }

    private fun customerLink(ticket: SupportTicket) = "${properties.baseUrl}/klantenservice/ticket/${ticket.accessToken}"

    private companion object {
        const val MAX_SUBJECT = 200
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
