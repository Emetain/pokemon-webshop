package com.emetain.pokemonwebshop.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Saves the value of every collection. Runs after the nightly card sync, so it uses the prices of that night. */
@Component
class CollectionSnapshotJob(
    private val historyService: CollectionHistoryService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.collection.snapshot-cron}", zone = "Europe/Amsterdam")
    fun run() {
        val userIds = historyService.usersWithACollection()
        val failed = userIds.count { !snapshot(it) }
        log.info("Saved the value of {} collections, {} failed", userIds.size - failed, failed)
    }

    /** One customer failing doesn't stop the others. Their graph simply has no point for today. */
    private fun snapshot(userId: Long): Boolean =
        try {
            historyService.snapshot(userId)
            true
        } catch (exception: Exception) {
            log.warn("No value snapshot for user {}: {}", userId, exception.message)
            false
        }
}
