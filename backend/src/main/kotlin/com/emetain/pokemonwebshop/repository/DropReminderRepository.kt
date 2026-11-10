package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.DropReminder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface DropReminderRepository : JpaRepository<DropReminder, Long> {

    fun findByDropIdAndEmail(dropId: Long, email: String): DropReminder?

    @Query("select r from DropReminder r where r.drop.id = :dropId and r.sentAt is null order by r.id")
    fun findUnsent(dropId: Long): List<DropReminder>

    fun countByDropId(dropId: Long): Long
}
