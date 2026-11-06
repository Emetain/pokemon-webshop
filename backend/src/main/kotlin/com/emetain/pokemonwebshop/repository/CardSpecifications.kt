package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.Game
import org.springframework.data.jpa.domain.Specification

/** The filters on card lists. A filter without a value is left out. */
object CardSpecifications {

    fun inSet(set: CardSet) = Specification<Card> { root, _, builder ->
        builder.equal(root.get<CardSet>("set"), set)
    }

    fun ofGame(game: Game?): Specification<Card>? = game?.let {
        Specification { root, _, builder -> builder.equal(root.get<Game>("game"), it) }
    }

    fun inSetWithCode(code: String?): Specification<Card>? = code?.let {
        Specification { root, _, builder -> builder.equal(root.get<CardSet>("set").get<String>("code"), it) }
    }

    fun withRarity(rarity: String?): Specification<Card>? = rarity?.let {
        Specification { root, _, builder -> builder.equal(root.get<String>("rarity"), it) }
    }

    fun withVariant(variant: String?): Specification<Card>? = variant?.let {
        Specification { root, _, builder -> builder.equal(root.join<Card, String>("variants"), it) }
    }

    fun withCategory(category: String?): Specification<Card>? = category?.let {
        Specification { root, _, builder -> builder.equal(root.get<String>("category"), it) }
    }

    fun withColor(color: String?): Specification<Card>? = color?.let {
        Specification { root, _, builder -> builder.equal(root.join<Card, String>("colors"), it) }
    }

    fun withPrice(required: Boolean): Specification<Card>? =
        if (required) Specification { root, _, builder -> builder.isNotNull(root.get<Long>("sortPriceCents")) } else null

    /** [term] is already in `nameSearch` form. */
    fun nameContains(term: String) = Specification<Card> { root, _, builder ->
        builder.like(root.get("nameSearch"), "%$term%")
    }

    /** Always ends on number and id, because a card has to stay in its place from one page to the next. */
    fun sortedBy(sort: CardSort, term: String? = null) = Specification<Card> { root, query, builder ->
        // The count query for the total runs through here too, and can't have an order.
        if (query != null && query.resultType != Long::class.javaObjectType) {
            val name = root.get<String>("nameSearch")
            val price = root.get<Long>("sortPriceCents")
            val cardsWithoutPriceLast = builder.asc(
                builder.selectCase<Int>().`when`(builder.isNull(price), 1).otherwise(0),
            )
            val startsWithTermFirst = builder.asc(
                builder.selectCase<Int>().`when`(builder.like(name, "$term%"), 0).otherwise(1),
            )
            val first = when (sort) {
                CardSort.BEST_MATCH -> listOf(startsWithTermFirst, builder.asc(name))
                CardSort.NUMBER -> emptyList()
                CardSort.NAME -> listOf(builder.asc(name))
                CardSort.NAME_DESCENDING -> listOf(builder.desc(name))
                CardSort.PRICE_HIGHEST -> listOf(cardsWithoutPriceLast, builder.desc(price))
                CardSort.PRICE_LOWEST -> listOf(cardsWithoutPriceLast, builder.asc(price))
            }
            query.orderBy(first + builder.asc(root.get<String>("sortKey")) + builder.asc(root.get<Long>("id")))
        }
        null
    }
}

/** How a list of cards can be sorted. [param] is the value in the URL. */
enum class CardSort(val param: String?) {
    BEST_MATCH(null),
    NUMBER("number"),
    NAME("name"),
    NAME_DESCENDING("name-desc"),
    PRICE_HIGHEST("price-high"),
    PRICE_LOWEST("price-low"),
    ;

    companion object {
        fun fromParam(param: String?): CardSort? = param?.let { value -> entries.firstOrNull { it.param == value } }
    }
}
