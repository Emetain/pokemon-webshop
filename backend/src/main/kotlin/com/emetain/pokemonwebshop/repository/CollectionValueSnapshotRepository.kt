package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.CollectionValueSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface CollectionValueSnapshotRepository : JpaRepository<CollectionValueSnapshot, Long> {
    fun findAllByUserIdOrderBySnapshotDate(userId: Long): List<CollectionValueSnapshot>

    fun findByUserIdAndSnapshotDate(userId: Long, snapshotDate: LocalDate): CollectionValueSnapshot?
}
