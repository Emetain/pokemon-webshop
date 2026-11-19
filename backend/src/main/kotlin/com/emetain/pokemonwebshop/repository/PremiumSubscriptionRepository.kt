package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.PremiumSubscription
import org.springframework.data.jpa.repository.JpaRepository

interface PremiumSubscriptionRepository : JpaRepository<PremiumSubscription, Long> {
    fun findByStripeSubscriptionId(stripeSubscriptionId: String): PremiumSubscription?

    fun findFirstByUserIdOrderByUpdatedAtDescIdDesc(userId: Long): PremiumSubscription?

    fun findAllByUserId(userId: Long): List<PremiumSubscription>
}
