package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.service.pictures.TcgGroup
import com.emetain.pokemonwebshop.service.pictures.TcgProduct
import com.emetain.pokemonwebshop.service.pictures.TcgplayerCatalog
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Stands in for tcgcsv.com, so tests never call it. Tests add the sets and products they need. */
class FakeTcgplayerCatalog : TcgplayerCatalog {
    val groups = ConcurrentHashMap<Game, CopyOnWriteArrayList<TcgGroup>>()
    val products = ConcurrentHashMap<Long, List<TcgProduct>>()

    fun add(game: Game, group: TcgGroup, products: List<TcgProduct> = emptyList()) {
        groups.computeIfAbsent(game) { CopyOnWriteArrayList() } += group
        this.products[group.id] = products
    }

    override fun groups(game: Game): List<TcgGroup> = groups[game].orEmpty()

    override fun products(game: Game, groupId: Long): List<TcgProduct> = products[groupId].orEmpty()
}

@TestConfiguration
class TestTcgplayerConfig {
    @Bean
    @Primary
    fun fakeTcgplayerCatalog() = FakeTcgplayerCatalog()
}
