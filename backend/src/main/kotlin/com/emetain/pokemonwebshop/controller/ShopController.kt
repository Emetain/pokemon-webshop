package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.dto.HeroSlideDto
import com.emetain.pokemonwebshop.dto.ShopSeriesDetailDto
import com.emetain.pokemonwebshop.dto.ShopSeriesDto
import com.emetain.pokemonwebshop.orchestration.HeroSlideOrchestrator
import com.emetain.pokemonwebshop.orchestration.ShopNavigationOrchestrator
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The parts of the shop around the products: the navigation by series and set, and the hero on the home page. */
@RestController
@RequestMapping("/v1/shop")
class ShopController(
    private val navigationOrchestrator: ShopNavigationOrchestrator,
    private val heroSlideOrchestrator: HeroSlideOrchestrator,
) {

    @GetMapping("/{game}/series")
    fun series(@PathVariable game: String): List<ShopSeriesDto> =
        navigationOrchestrator.series(game)

    @GetMapping("/{game}/series/{slug}")
    fun seriesDetail(@PathVariable game: String, @PathVariable slug: String): ShopSeriesDetailDto =
        navigationOrchestrator.seriesDetail(game, slug)

    @GetMapping("/hero-slides")
    fun heroSlides(): List<HeroSlideDto> =
        heroSlideOrchestrator.showing()
}
