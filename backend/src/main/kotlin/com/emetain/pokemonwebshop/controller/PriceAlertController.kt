package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.PriceAlertDto
import com.emetain.pokemonwebshop.dto.PriceAlertListDto
import com.emetain.pokemonwebshop.dto.PriceAlertOffDto
import com.emetain.pokemonwebshop.dto.PriceAlertOffRequest
import com.emetain.pokemonwebshop.dto.PriceAlertRequest
import com.emetain.pokemonwebshop.orchestration.PriceAlertOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RequiresLogin
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Price alerts on cards (Premium, phase 12), mailed once a day after the sync. */
@RestController
@RequestMapping("/v1/price-alerts")
class PriceAlertController(
    private val orchestrator: PriceAlertOrchestrator,
) {

    @GetMapping
    @RequiresLogin
    fun list(@CurrentUser user: User): PriceAlertListDto = orchestrator.list(user)

    @GetMapping("/cards/{cardId}")
    @RequiresLogin
    fun forCard(@CurrentUser user: User, @PathVariable cardId: Long): PriceAlertListDto = orchestrator.forCard(user, cardId)

    /** Sets an alert, or changes the one of the same kind on that variant. */
    @PostMapping
    @RequiresLogin
    fun save(@CurrentUser user: User, @Valid @RequestBody request: PriceAlertRequest): PriceAlertDto =
        orchestrator.save(user, request)

    @DeleteMapping("/{id}")
    @RequiresLogin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@CurrentUser user: User, @PathVariable id: Long) = orchestrator.delete(user, id)

    /** The link in an alert mail: switches that alert off without logging in. */
    @PostMapping("/off")
    fun switchOff(@Valid @RequestBody request: PriceAlertOffRequest): PriceAlertOffDto =
        orchestrator.switchOff(request.token!!)
}
