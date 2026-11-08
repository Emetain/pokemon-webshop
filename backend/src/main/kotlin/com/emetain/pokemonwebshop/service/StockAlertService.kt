package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.StockAlert
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.repository.StockAlertRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Clock
import java.util.Locale

/** "Mail me when back in stock": one mail per signup, as soon as the product can be bought again. */
@Service
class StockAlertService(
    private val alertRepository: StockAlertRepository,
    private val productRepository: ProductRepository,
    private val mailService: MailService,
    private val clock: Clock,
) {

    /** Signing up twice is fine. After a mail, signing up again waits for the next time. */
    @Transactional
    fun signUp(productId: Long, email: String) {
        val address = email.trim().lowercase(Locale.ROOT)
        if (!EMAIL.matches(address) || address.length > MAX_EMAIL_LENGTH) {
            throw ApiException.validation("Vul een geldig e-mailadres in.")
        }
        val product = productRepository.findWithDetailsById(productId) ?: throw ApiException.notFound()
        if (ProductRules.canBuy(product)) {
            throw ApiException.validation("Dit product is op voorraad. Je kunt het nu bestellen.")
        }
        val existing = alertRepository.findByProductIdAndEmail(productId, address)
        if (existing == null) {
            alertRepository.saveAndFlush(StockAlert(product, address, clock.instant()))
        } else if (existing.notifiedAt != null) {
            existing.notifiedAt = null
            existing.createdAt = clock.instant()
        }
    }

    /** After the change is saved, so nobody gets a mail about stock that was rolled back. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onAvailabilityChanged(event: ProductAvailabilityChanged) = notifyIfBuyable(event.productId)

    @Transactional
    fun notifyIfBuyable(productId: Long) {
        val product = productRepository.findWithDetailsById(productId) ?: return
        if (!ProductRules.canBuy(product)) return
        val now = clock.instant()
        for (alert in alertRepository.findWaiting(productId)) {
            alert.notifiedAt = now
            mailService.sendBackInStock(alert.email, product.name, product.slug)
        }
    }

    private companion object {
        const val MAX_EMAIL_LENGTH = 254
        val EMAIL = Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
    }
}
