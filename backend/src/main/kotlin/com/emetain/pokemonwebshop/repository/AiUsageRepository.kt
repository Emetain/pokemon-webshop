package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.AiUsage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface AiUsageRepository : JpaRepository<AiUsage, Long> {

    @Query("select coalesce(sum(u.costMicros), 0) from AiUsage u where u.createdAt >= :from and u.kind in :kinds")
    fun costSince(from: Instant, kinds: Collection<String>): Long
}
