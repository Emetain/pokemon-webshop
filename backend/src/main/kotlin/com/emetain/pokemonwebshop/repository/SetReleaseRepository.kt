package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.SetRelease
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface SetReleaseRepository : JpaRepository<SetRelease, Long> {

    @Query(
        """
        select r from SetRelease r where r.published = true and r.releaseDate >= :from and (:game is null or r.game = :game)
        order by r.releaseDate asc, r.name asc
        """,
    )
    fun findPublishedFrom(from: LocalDate, game: Game?): List<SetRelease>

    @Query("select r from SetRelease r order by r.releaseDate desc, r.name asc")
    fun findAllForAdmin(): List<SetRelease>
}
