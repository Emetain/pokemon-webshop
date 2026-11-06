package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.SyncRun
import com.emetain.pokemonwebshop.domain.SyncStatus
import com.emetain.pokemonwebshop.repository.SyncRunRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** Keeps the log of sync runs. Every step is its own transaction, so the admin page sees progress live. */
@Service
class SyncRunService(
    private val syncRunRepository: SyncRunRepository,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun start(game: Game): SyncRun = syncRunRepository.save(SyncRun(game = game, startedAt = clock.instant()))

    @Transactional
    fun addProgress(runId: Long, result: ImportResult) {
        val run = syncRunRepository.findById(runId).orElseThrow()
        run.setsProcessed++
        run.cardsCreated += result.created
        run.cardsUpdated += result.updated
        run.cardsUnchanged += result.unchanged
        run.cardsFailed += result.failed
        run.pricesStored += result.pricesStored
    }

    @Transactional
    fun finish(runId: Long, status: SyncStatus, error: String? = null) {
        val run = syncRunRepository.findById(runId).orElseThrow()
        run.status = status
        run.finishedAt = clock.instant()
        run.error = error?.take(MAX_ERROR_LENGTH)
    }

    /** A run that is still marked as running after a start can't be running: its thread died with the app. */
    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun failInterruptedRuns() {
        for (run in syncRunRepository.findAllByStatus(SyncStatus.RUNNING)) {
            log.warn("Sync run {} for {} was interrupted by a restart", run.id, run.game)
            run.status = SyncStatus.FAILED
            run.finishedAt = clock.instant()
            run.error = "Interrupted by a restart of the application"
        }
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 2000
    }
}
