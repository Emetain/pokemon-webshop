package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.PriceAlertDto
import com.emetain.pokemonwebshop.dto.PriceAlertListDto
import com.emetain.pokemonwebshop.dto.PriceAlertOffDto
import com.emetain.pokemonwebshop.dto.PriceAlertRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.service.PremiumService
import com.emetain.pokemonwebshop.service.PriceAlertService
import com.emetain.pokemonwebshop.service.PriceAlertView
import org.springframework.stereotype.Component

/** Price alerts (Premium, phase 12). Looking at them and removing them also works after Premium ended. */
@Component
class PriceAlertOrchestrator(
    private val alertService: PriceAlertService,
    private val premiumService: PremiumService,
    properties: AppProperties,
) {
    private val max = properties.premium.maxPriceAlerts

    fun list(user: User): PriceAlertListDto =
        PriceAlertListDto(premiumService.has(user), max, alertService.list(user.id).map(PriceAlertDto::from))

    /** The alerts on one card; for a customer without Premium the page shows the Premium popup instead. */
    fun forCard(user: User, cardId: Long): PriceAlertListDto {
        val alerts = alertService.list(user.id).filter { it.alert.card.id == cardId }
        return PriceAlertListDto(premiumService.has(user), max, alerts.map(PriceAlertDto::from))
    }

    fun save(user: User, request: PriceAlertRequest): PriceAlertDto {
        val alert = alertService.save(
            user,
            request.cardId ?: throw ApiException.validation("Kies een kaart."),
            request.variant,
            request.kind ?: throw ApiException.validation("Kies wat voor alert je wilt."),
            request.thresholdCents,
            request.percent,
        )
        val view = alertService.list(user.id).firstOrNull { it.alert.id == alert.id } ?: PriceAlertView(alert, null)
        return PriceAlertDto.from(view)
    }

    fun delete(user: User, id: Long) = alertService.delete(user.id, id)

    fun switchOff(token: String): PriceAlertOffDto {
        val name = alertService.switchOff(token)
            ?: throw ApiException.notFound("Deze link werkt niet meer. De alert staat misschien al uit, of er kwam een nieuwere mail.")
        return PriceAlertOffDto(name)
    }
}
