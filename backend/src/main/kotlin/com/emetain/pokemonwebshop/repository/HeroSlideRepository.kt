package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.HeroSlide
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface HeroSlideRepository : JpaRepository<HeroSlide, Long> {
    @Query("select s from HeroSlide s order by s.position, s.id")
    fun findAllInOrder(): List<HeroSlide>

    @Query("select coalesce(max(s.position), 0) from HeroSlide s")
    fun findHighestPosition(): Int
}
