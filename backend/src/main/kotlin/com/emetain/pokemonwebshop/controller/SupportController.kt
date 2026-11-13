package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminTicketDto
import com.emetain.pokemonwebshop.dto.AdminTicketPageDto
import com.emetain.pokemonwebshop.dto.AdminTicketReplyRequest
import com.emetain.pokemonwebshop.dto.ChatAnswerDto
import com.emetain.pokemonwebshop.dto.ChatRequest
import com.emetain.pokemonwebshop.dto.ChatStatusDto
import com.emetain.pokemonwebshop.dto.CreateTicketRequest
import com.emetain.pokemonwebshop.dto.TicketDto
import com.emetain.pokemonwebshop.dto.TicketReplyRequest
import com.emetain.pokemonwebshop.dto.TicketStatusRequest
import com.emetain.pokemonwebshop.dto.TicketSummaryDto
import com.emetain.pokemonwebshop.orchestration.SupportOrchestrator
import com.emetain.pokemonwebshop.security.AdminOnly
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RateLimited
import com.emetain.pokemonwebshop.security.RequiresLogin
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Customer service for everyone: the chatbot, making a ticket, and following it with the link from the mail. */
@RestController
@RequestMapping("/v1/support")
class SupportController(
    private val supportOrchestrator: SupportOrchestrator,
) {

    @GetMapping("/chat")
    fun chatStatus(): ChatStatusDto = supportOrchestrator.chatStatus()

    @PostMapping("/chat")
    @RateLimited("support-chat")
    fun chat(@CurrentUser viewer: User?, @Valid @RequestBody request: ChatRequest): ChatAnswerDto = supportOrchestrator.chat(request, viewer)

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimited("ticket")
    fun create(@CurrentUser viewer: User?, @Valid @RequestBody request: CreateTicketRequest): TicketDto =
        supportOrchestrator.create(request, viewer)

    /** [token] is the secret from the link in the mail. */
    @GetMapping("/tickets/{token}")
    fun ticket(@PathVariable token: String): TicketDto = supportOrchestrator.ticket(token)

    @PostMapping("/tickets/{token}/messages")
    @RateLimited("ticket-reply")
    fun reply(@PathVariable token: String, @Valid @RequestBody request: TicketReplyRequest): TicketDto =
        supportOrchestrator.reply(token, request)
}

/** "Mijn tickets": the tickets sent from this account. */
@RestController
@RequestMapping("/v1/account/tickets")
@RequiresLogin
class AccountTicketController(
    private val supportOrchestrator: SupportOrchestrator,
) {

    @GetMapping
    fun mine(@CurrentUser viewer: User): List<TicketSummaryDto> = supportOrchestrator.mine(viewer)
}

@RestController
@RequestMapping("/v1/admin/tickets")
@AdminOnly
class AdminTicketController(
    private val supportOrchestrator: SupportOrchestrator,
) {

    /** `status` is `OPEN`, `ANSWERED`, `CLOSED`, or empty for all. */
    @GetMapping
    fun list(@RequestParam(required = false) status: String?, @RequestParam(defaultValue = "0") page: Int): AdminTicketPageDto =
        supportOrchestrator.adminList(status, page)

    @GetMapping("/{number}")
    fun ticket(@PathVariable number: String): AdminTicketDto = supportOrchestrator.adminTicket(number)

    @PostMapping("/{number}/messages")
    fun reply(@PathVariable number: String, @CurrentUser admin: User, @Valid @RequestBody request: AdminTicketReplyRequest): AdminTicketDto =
        supportOrchestrator.adminReply(number, admin, request)

    @PutMapping("/{number}/status")
    fun status(@PathVariable number: String, @Valid @RequestBody request: TicketStatusRequest): AdminTicketDto =
        supportOrchestrator.adminStatus(number, request)
}
