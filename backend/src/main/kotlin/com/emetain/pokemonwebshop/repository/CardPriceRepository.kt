package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardPrice
import com.emetain.pokemonwebshop.domain.Game
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CardPriceRepository : JpaRepository<CardPrice, Long> {
    fun findAllByCardIn(cards: Collection<Card>): List<CardPrice>

    @Query("select p from CardPrice p where p.card.id in :cardIds")
    fun findAllByCardIds(cardIds: Collection<Long>): List<CardPrice>

    @Query(
        "select new com.emetain.pokemonwebshop.repository.OwnedPrice(p.card.id, p.variant, p.trendCents, p.currency) " +
            "from CardPrice p where p.trendCents is not null " +
            "and p.card.id in (select e.card.id from CollectionEntry e where e.user.id = :userId)",
    )
    fun findOwnedPrices(userId: Long): List<OwnedPrice>

    @Query(
        "select new com.emetain.pokemonwebshop.repository.OwnedPrice(p.card.id, p.variant, p.trendCents, p.currency) " +
            "from CardPrice p where p.trendCents is not null and p.card.set.id = :setId " +
            "and p.card.id in (select e.card.id from CollectionEntry e where e.user.id = :userId)",
    )
    fun findOwnedPricesInSet(userId: Long, setId: Long): List<OwnedPrice>

    @Query("select count(p) from CardPrice p where p.card.game = :game")
    fun countByGame(game: Game): Long
}
