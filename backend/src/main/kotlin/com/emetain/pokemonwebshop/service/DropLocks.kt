package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Drop
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Component

/**
 * The lock on a drop. Every change to its stock, waiting room or limits takes it first, inside a transaction, and keeps
 * it until the transaction ends.
 */
@Component
class DropLocks(
    private val entityManager: EntityManager,
) {
    /**
     * Locks the row and reads it again. A drop loaded earlier in the same transaction, for example to find it by its
     * slug, would otherwise keep the numbers from before the lock, such as the next place in line.
     */
    fun lock(dropId: Long): Drop? {
        val drop = entityManager.find(Drop::class.java, dropId, LockModeType.PESSIMISTIC_WRITE) ?: return null
        entityManager.refresh(drop)
        return drop
    }
}
