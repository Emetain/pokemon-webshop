package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.SupportTicket
import com.emetain.pokemonwebshop.domain.TicketStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SupportTicketRepository : JpaRepository<SupportTicket, Long> {

    @EntityGraph(attributePaths = ["messages"])
    fun findByAccessToken(accessToken: String): SupportTicket?

    @EntityGraph(attributePaths = ["messages", "messages.author"])
    fun findByNumber(number: String): SupportTicket?

    fun existsByNumber(number: String): Boolean

    fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<SupportTicket>

    /** Waiting for the shop first, the longest waiting on top. Then the rest, newest first. */
    @Query(
        """
        select t from SupportTicket t where (:status is null or t.status = :status)
        order by case when t.status = 'OPEN' then 0 else 1 end,
                 case when t.status = 'OPEN' then t.updatedAt end asc,
                 t.updatedAt desc, t.id desc
        """,
    )
    fun findForAdmin(status: TicketStatus?, pageable: Pageable): Page<SupportTicket>

    fun countByStatus(status: TicketStatus): Long
}
