package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdsDto
import com.emetain.pokemonwebshop.dto.CompanyDto
import com.emetain.pokemonwebshop.dto.HeroSlideDto
import com.emetain.pokemonwebshop.dto.ShopSeriesDetailDto
import com.emetain.pokemonwebshop.dto.ShopSeriesDto
import com.emetain.pokemonwebshop.orchestration.AdsOrchestrator
import com.emetain.pokemonwebshop.orchestration.CompanyOrchestrator
import com.emetain.pokemonwebshop.orchestration.HeroSlideOrchestrator
import com.emetain.pokemonwebshop.orchestration.ShopNavigationOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The parts of the shop around the products: the navigation by series and set, the hero on the home page, and the
 * business details in the footer.
 */
@RestController
@RequestMapping("/v1/shop")
class ShopController(
    private val navigationOrchestrator: ShopNavigationOrchestrator,
    private val heroSlideOrchestrator: HeroSlideOrchestrator,
    private val companyOrchestrator: CompanyOrchestrator,
    private val adsOrchestrator: AdsOrchestrator,
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

    @GetMapping("/company")
    fun company(): CompanyDto =
        companyOrchestrator.company()

    /** Whether the free card pages show ads, and which ad units. Never for a Premium member (phase 12). */
    @GetMapping("/ads")
    fun ads(@CurrentUser viewer: User?): AdsDto =
        adsOrchestrator.ads(viewer)
}
