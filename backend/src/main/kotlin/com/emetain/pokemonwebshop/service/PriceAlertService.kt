package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardPrice
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.PriceAlert
import com.emetain.pokemonwebshop.domain.PriceAlertKind
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.PriceAlertRepository
import com.emetain.pokemonwebshop.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** The price a card has today per variant, as the card page shows it: the trend price. */
object CurrentPrices {
    fun of(prices: List<CardPrice>): Map<Pair<Long, String>, Price> = prices
        .filter { it.trendCents != null }
        .sortedBy { it.source }
        .groupBy { it.card.id to it.variant }
        .mapValues { (_, rows) -> rows.first().let { Price(it.trendCents!!, it.currency) } }
}

/** An alert with the card's price today, for the lists. */
data class PriceAlertView(val alert: PriceAlert, val price: Price?)

/** One alert that went off, with the one-off link that switches it off. */
data class AlertMailLine(val hit: AlertHit, val offToken: String)

/** The day's alert mail of one customer. */
data class AlertMail(val email: String, val displayName: String, val lines: List<AlertMailLine>)

/**
 * Price alerts (Premium, phase 12): set on a card, checked once a day after the sync, at most one mail a day per
 * customer. While Premium has ended the alerts stay, aren't checked, and can still be looked at and removed.
 */
@Service
class PriceAlertService(
    private val alertRepository: PriceAlertRepository,
    private val cardRepository: CardRepository,
    private val cardPriceRepository: CardPriceRepository,
    private val userRepository: UserRepository,
    private val premiumService: PremiumService,
    private val exchangeRateService: ExchangeRateService,
    private val tokenService: TokenService,
    private val clock: Clock,
    properties: AppProperties,
) {
    private val maxAlerts = properties.premium.maxPriceAlerts

    @Transactional(readOnly = true)
    fun list(userId: Long): List<PriceAlertView> {
        val alerts = alertRepository.findAllOfUser(userId)
        val prices = prices(alerts.map { it.card.id })
        return alerts.map { PriceAlertView(it, prices[it.card.id to it.variant]) }
    }

    @Transactional(readOnly = true)
    fun forCard(userId: Long, cardId: Long): List<PriceAlert> = alertRepository.findAllByUserIdAndCardId(userId, cardId)

    /**
     * Sets an alert, or changes the one of this kind the customer already has on this variant. Below a price takes
     * [thresholdCents] in the customer's currency; up or down takes [percent].
     */
    @Transactional
    fun save(user: User, cardId: Long, variant: String?, kind: PriceAlertKind, thresholdCents: Long?, percent: Int?): PriceAlert {
        premiumService.require(user, "Prijsalerts horen bij Premium.")
        val card = cardRepository.findWithSetById(cardId) ?: throw ApiException.notFound("Deze kaart bestaat niet.")
        val chosen = variantOf(card, variant)
        when (kind) {
            PriceAlertKind.BELOW -> if (thresholdCents == null || thresholdCents < 1 || thresholdCents > MAX_THRESHOLD_CENTS) {
                throw ApiException.validation("Vul een prijs in tussen 0,01 en 1.000.000.")
            }
            PriceAlertKind.CHANGE -> if (percent == null || percent !in PriceAlertRules.MIN_PERCENT..PriceAlertRules.MAX_PERCENT) {
                throw ApiException.validation(
                    "Vul een percentage in tussen ${PriceAlertRules.MIN_PERCENT} en ${PriceAlertRules.MAX_PERCENT}.",
                )
            }
        }

        val existing = alertRepository.findAllByUserIdAndCardId(user.id, cardId).firstOrNull { it.variant == chosen && it.kind == kind }
        if (existing == null && alertRepository.countByUserId(user.id) >= maxAlerts) {
            throw ApiException.validation("Je kunt maximaal $maxAlerts prijsalerts hebben. Zet er eerst een uit.")
        }
        val alert = existing ?: PriceAlert(
            user = userRepository.getReferenceById(user.id),
            card = card,
            variant = chosen,
            kind = kind,
            createdAt = clock.instant(),
        )
        alert.thresholdCents = thresholdCents.takeIf { kind == PriceAlertKind.BELOW }
        alert.currency = user.currency.takeIf { kind == PriceAlertKind.BELOW }
        alert.percent = percent.takeIf { kind == PriceAlertKind.CHANGE }
        PriceAlertRules.start(alert, prices(listOf(cardId))[cardId to chosen])
        return try {
            alertRepository.saveAndFlush(alert)
        } catch (exception: DataIntegrityViolationException) {
            // The same alert, set twice at once.
            throw ApiException.validation("Deze prijsalert staat al aan.")
        }
    }

    /** Removing is always allowed, also after Premium ended: nothing should keep mailing a customer who wants out. */
    @Transactional
    fun delete(userId: Long, id: Long) {
        val alert = alertRepository.findByIdAndUserId(id, userId) ?: throw ApiException.notFound()
        alertRepository.delete(alert)
    }

    /** The link in an alert mail, which works without logging in. Returns the card's name, or null for an unknown link. */
    @Transactional
    fun switchOff(token: String): String? {
        val alert = alertRepository.findByOffTokenHash(tokenService.hash(token)) ?: return null
        val name = alert.card.name
        alertRepository.delete(alert)
        return name
    }

    /**
     * Checks one customer's alerts against today's prices and saves what changed. Returns the mail to send, or null:
     * no alert went off, the customer has no Premium (their alerts wait), or a mail already went out today.
     */
    @Transactional
    fun check(userId: Long): AlertMail? {
        val user = userRepository.findById(userId).orElse(null) ?: return null
        if (!premiumService.has(user)) return null
        val today = today()
        val alerts = alertRepository.findAllOfUser(userId)
        if (alerts.any { it.lastTriggeredOn == today }) return null
        val prices = prices(alerts.map { it.card.id })
        val rates = exchangeRateService.current()
        val lines = alerts.mapNotNull { alert ->
            val hit = PriceAlertRules.check(alert, prices[alert.card.id to alert.variant], rates, today) ?: return@mapNotNull null
            val token = tokenService.generate()
            alert.offTokenHash = tokenService.hash(token)
            AlertMailLine(hit, token)
        }
        return lines.takeIf { it.isNotEmpty() }?.let { AlertMail(user.email, user.displayName, it) }
    }

    fun userIdsWithAlerts(): List<Long> = alertRepository.findUserIds()

    private fun prices(cardIds: Collection<Long>): Map<Pair<Long, String>, Price> =
        if (cardIds.isEmpty()) emptyMap() else CurrentPrices.of(cardPriceRepository.findAllByCardIds(cardIds.toSet()))

    private fun today(): LocalDate = clock.instant().atZone(SHOP_ZONE).toLocalDate()

    private companion object {
        val SHOP_ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        const val MAX_THRESHOLD_CENTS = 100_000_000L
    }
}

/** The variant an alert or binder entry is for: the one asked for, or the card's main one, like the collection. */
fun variantOf(card: Card, variant: String?): String {
    val chosen = variant ?: CardVariants.default(card.variants)
    if (chosen !in card.variants) throw ApiException.validation("Deze kaart bestaat niet in deze variant.")
    return chosen
}
