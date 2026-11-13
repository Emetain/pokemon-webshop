package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.SupportMessage
import com.emetain.pokemonwebshop.domain.SupportTicket
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateTicketRequest(
    @field:Size(max = 100, message = "Je naam mag maximaal 100 tekens hebben.")
    val name: String = "",
    @field:Size(max = 254, message = "Dit e-mailadres is te lang.")
    val email: String = "",
    @field:Size(max = 20, message = "Dit bestelnummer is te lang.")
    val orderNumber: String? = null,
    @field:Size(max = 150, message = "Het onderwerp mag maximaal 150 tekens hebben.")
    val subject: String = "",
    @field:Size(max = 5000, message = "Je bericht mag maximaal 5000 tekens hebben.")
    val message: String = "",
    /** The conversation with the chatbot, when the customer chose to add it. */
    @field:Size(max = 20, message = "Het gesprek met de assistent is te lang om mee te sturen.")
    @field:Valid
    val chat: List<ChatTurn> = emptyList(),
    override val website: String? = null,
) : HoneypotRequest

data class TicketReplyRequest(
    @field:Size(max = 5000, message = "Je bericht mag maximaal 5000 tekens hebben.")
    val message: String = "",
)

data class AdminTicketReplyRequest(
    @field:Size(max = 5000, message = "Je antwoord mag maximaal 5000 tekens hebben.")
    val message: String = "",
    /** Close the ticket with this answer. */
    val close: Boolean = false,
)

data class TicketStatusRequest(
    /** `OPEN` or `CLOSED`. */
    val status: String = "",
)

data class TicketMessageDto(
    val fromShop: Boolean,
    val body: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(message: SupportMessage) = TicketMessageDto(message.fromShop, message.body, message.createdAt)
    }
}

/** A ticket as the customer sees it, through the link in the mail or in the account. */
data class TicketDto(
    val number: String,
    /** For the link to the ticket. Only ever sent to someone who has it already, or whose account it is. */
    val token: String,
    val subject: String,
    val status: String,
    val orderNumber: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messages: List<TicketMessageDto>,
) {
    companion object {
        fun from(ticket: SupportTicket) = TicketDto(
            number = ticket.number,
            token = ticket.accessToken,
            subject = ticket.subject,
            status = ticket.status.name,
            orderNumber = ticket.orderNumber,
            createdAt = ticket.createdAt,
            updatedAt = ticket.updatedAt,
            messages = ticket.messages.map(TicketMessageDto::from),
        )
    }
}

/** A ticket in the list under "Mijn tickets". */
data class TicketSummaryDto(
    val number: String,
    val token: String,
    val subject: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(ticket: SupportTicket) =
            TicketSummaryDto(ticket.number, ticket.accessToken, ticket.subject, ticket.status.name, ticket.createdAt, ticket.updatedAt)
    }
}

data class AdminTicketMessageDto(
    val fromShop: Boolean,
    val body: String,
    val createdAt: Instant,
    /** The admin who answered. */
    val author: String?,
) {
    companion object {
        fun from(message: SupportMessage) = AdminTicketMessageDto(message.fromShop, message.body, message.createdAt, message.author?.displayName)
    }
}

data class AdminTicketDto(
    val number: String,
    val subject: String,
    val status: String,
    val name: String,
    val email: String,
    val orderNumber: String?,
    /** Sent from an account, not as a guest. */
    val hasAccount: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messages: List<AdminTicketMessageDto>,
) {
    companion object {
        fun from(ticket: SupportTicket) = AdminTicketDto(
            number = ticket.number,
            subject = ticket.subject,
            status = ticket.status.name,
            name = ticket.name,
            email = ticket.email,
            orderNumber = ticket.orderNumber,
            hasAccount = ticket.user != null,
            createdAt = ticket.createdAt,
            updatedAt = ticket.updatedAt,
            messages = ticket.messages.map(AdminTicketMessageDto::from),
        )
    }
}

data class AdminTicketSummaryDto(
    val number: String,
    val subject: String,
    val status: String,
    val name: String,
    val email: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(ticket: SupportTicket) =
            AdminTicketSummaryDto(ticket.number, ticket.subject, ticket.status.name, ticket.name, ticket.email, ticket.createdAt, ticket.updatedAt)
    }
}

data class AdminTicketPageDto(
    val tickets: List<AdminTicketSummaryDto>,
    val page: Int,
    val totalPages: Int,
    /** Per status, for the tabs. */
    val counts: Map<String, Long>,
)

/** One line of a conversation with the chatbot. The browser keeps the conversation: the shop stores nothing. */
data class ChatTurn(
    /** `user` or `assistant`. */
    @field:Size(max = 10, message = "Onbekende afzender.")
    val role: String = "",
    @field:Size(max = 2000, message = "Een bericht aan de assistent mag maximaal 2000 tekens hebben.")
    val text: String = "",
)

data class ChatRequest(
    /** The conversation so far, the customer's new question last. */
    @field:Size(max = 30, message = "Dit gesprek is te lang. Begin een nieuw gesprek of maak een ticket.")
    @field:Valid
    val messages: List<ChatTurn> = emptyList(),
    override val website: String? = null,
) : HoneypotRequest

data class ChatAnswerDto(
    val answer: String,
    /** The assistent can't help with this: the page offers to make a ticket. */
    val suggestTicket: Boolean,
)

data class ChatStatusDto(
    /** False without an OpenAI key, or when this month's chat budget is used up: the page goes straight to a ticket. */
    val available: Boolean,
)
