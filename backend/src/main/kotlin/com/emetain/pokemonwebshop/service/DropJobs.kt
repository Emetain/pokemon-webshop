package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.DropStatus
import com.emetain.pokemonwebshop.repository.DropRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * The drop scheduler, every second by default: starts drops at their start time, lets the next batch through the
 * waiting room, ends drops at their end time or when everything is sold, and sends the reminders. One drop failing
 * doesn't stop the others, and the next tick tries again.
 */
@Component
class DropJobs(
    private val dropRepository: DropRepository,
    private val dropLocks: DropLocks,
    private val dropQueueService: DropQueueService,
    private val dropService: DropService,
    private val dropReminderService: DropReminderService,
    private val transactions: TransactionTemplate,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.drops.tick-cron}", zone = "Europe/Amsterdam")
    fun tick() {
        val now = clock.instant()
        for (dropId in dropRepository.findIdsToStart(now)) safely(dropId, "start") { dropQueueService.start(dropId) }
        for (dropId in dropRepository.findIdsWithStatus(DropStatus.LIVE)) safely(dropId, "advance") { advance(dropId) }
        for (dropId in dropRepository.findIdsToRemind(now.plus(properties.drops.reminderBefore))) {
            safely(dropId, "remind") { dropReminderService.send(dropId) }
        }
    }

    /** Ends a live drop that is over, or lets the next batch through. Returns how many got through. */
    fun advance(dropId: Long): Int = transactions.execute {
        val drop = dropLocks.lock(dropId) ?: return@execute 0
        if (drop.status != DropStatus.LIVE) return@execute 0
        val now = clock.instant()
        val endsAt = drop.endsAt
        when {
            endsAt != null && !endsAt.isAfter(now) -> {
                dropService.end(drop, "Eindtijd bereikt")
                0
            }
            drop.products.isNotEmpty() && drop.products.all { it.soldOut } -> {
                dropService.end(drop, "Uitverkocht")
                0
            }
            else -> dropQueueService.admit(drop, now)
        }
    }!!

    private fun safely(dropId: Long, step: String, action: () -> Unit) {
        try {
            action()
        } catch (exception: OptimisticLockingFailureException) {
            // Someone bought from the normal stock at the same moment the leftover went back. The next tick retries.
            log.info("Drop {} could not {} this time: the stock changed at the same moment", dropId, step)
        } catch (exception: RuntimeException) {
            log.error("Drop {} could not {}", dropId, step, exception)
        }
    }
}
