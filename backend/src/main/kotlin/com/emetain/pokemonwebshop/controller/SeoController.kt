package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.orchestration.SeoOrchestrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * For search engines and link previews. Caddy sends `/sitemap.xml`, `/robots.txt` and the product, drop and news
 * pages here (see deploy/Caddyfile).
 */
@RestController
@RequestMapping("/v1/seo")
class SeoController(
    private val seoOrchestrator: SeoOrchestrator,
) {

    @GetMapping("/sitemap.xml")
    fun sitemap(): ResponseEntity<String> = seoOrchestrator.sitemap()

    @GetMapping("/robots.txt")
    fun robots(): ResponseEntity<String> = seoOrchestrator.robots()

    @GetMapping("/pages/product/{slug}")
    fun productPage(@PathVariable slug: String): ResponseEntity<String> = seoOrchestrator.productPage(slug)

    @GetMapping("/pages/drops/{slug}")
    fun dropPage(@PathVariable slug: String): ResponseEntity<String> = seoOrchestrator.dropPage(slug)

    @GetMapping("/pages/nieuws/{slug}")
    fun newsPage(@PathVariable slug: String): ResponseEntity<String> = seoOrchestrator.newsPage(slug)
}
