package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.SyncRun
import com.emetain.pokemonwebshop.domain.SyncStatus
import org.springframework.data.jpa.repository.JpaRepository

interface SyncRunRepository : JpaRepository<SyncRun, Long> {
    fun findFirstByGameOrderByStartedAtDescIdDesc(game: Game): SyncRun?

    fun findAllByStatus(status: SyncStatus): List<SyncRun>
}
