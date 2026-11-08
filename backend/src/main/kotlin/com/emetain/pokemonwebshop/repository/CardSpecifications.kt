package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.CardWishlistEntry
import com.emetain.pokemonwebshop.domain.CollectionEntry
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.User
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate

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

    fun withDexNumber(dexNumber: Int) = Specification<Card> { root, _, builder ->
        builder.equal(root.join<Card, Int>("dexNumbers"), dexNumber)
    }

    fun ownedBy(userId: Long) = Specification<Card> { root, query, builder ->
        val entries = requireNotNull(query).subquery(Long::class.javaObjectType)
        val entry = entries.from(CollectionEntry::class.java)
        entries.select(entry.get("id")).where(
            builder.equal(entry.get<User>("user").get<Long>("id"), userId),
            builder.equal(entry.get<Card>("card"), root),
        )
        builder.exists(entries)
    }

    /** Matches normal set progress: a number counts as owned when any card with that number is. */
    fun numberMissingFor(userId: Long) = Specification<Card> { root, query, builder ->
        val entries = requireNotNull(query).subquery(Long::class.javaObjectType)
        val entry = entries.from(CollectionEntry::class.java)
        val owned = entry.join<CollectionEntry, Card>("card")
        entries.select(entry.get("id")).where(
            builder.equal(entry.get<User>("user").get<Long>("id"), userId),
            builder.equal(owned.get<CardSet>("set"), root.get<CardSet>("set")),
            builder.equal(owned.get<String>("number"), root.get<String>("number")),
        )
        builder.not(builder.exists(entries))
    }

    /** Matches master set progress: a card counts as missing until every variant is owned. */
    fun variantMissingFor(userId: Long) = Specification<Card> { root, query, builder ->
        val variants = requireNotNull(query).subquery(String::class.java)
        val card = variants.correlate(root)
        val variant = card.join<Card, String>("variants")

        val entries = variants.subquery(Long::class.javaObjectType)
        val entry = entries.from(CollectionEntry::class.java)
        entries.select(entry.get("id")).where(
            builder.equal(entry.get<User>("user").get<Long>("id"), userId),
            builder.equal(entry.get<Card>("card"), root),
            builder.equal(entry.get<String>("variant"), variant),
        )

        variants.select(variant).where(builder.not(builder.exists(entries)))
        builder.exists(variants)
    }

    fun onWishlistOf(userId: Long) = Specification<Card> { root, query, builder ->
        val entries = requireNotNull(query).subquery(Long::class.javaObjectType)
        val entry = entries.from(CardWishlistEntry::class.java)
        entries.select(entry.get("id")).where(
            builder.equal(entry.get<User>("user").get<Long>("id"), userId),
            builder.equal(entry.get<Card>("card"), root),
        )
        builder.exists(entries)
    }

    /** No picture at all: the source has none and the shop didn't upload one. */
    fun withoutImage() = Specification<Card> { root, _, builder ->
        builder.and(builder.isNull(root.get<String>("imageSmallUrl")), builder.isNull(root.get<String>("customImageUrl")))
    }

    fun withCustomImage() = Specification<Card> { root, _, builder ->
        builder.isNotNull(root.get<String>("customImageUrl"))
    }

    /** For the admin lists: newest set first, by name, or by Pokédex number with cards without one last. */
    fun sortedForAdmin(sort: AdminCardSort) = Specification<Card> { root, query, builder ->
        // The count query for the total runs through here too, and can't have an order.
        if (query != null && query.resultType != Long::class.javaObjectType) {
            val set = root.get<CardSet>("set")
            val releaseDate = set.get<LocalDate>("releaseDate")
            val newestSetFirst = listOf(
                builder.asc(builder.selectCase<Int>().`when`(builder.isNull(releaseDate), 1).otherwise(0)),
                builder.desc(releaseDate),
                builder.desc(set.get<Int>("sortOrder")),
                builder.asc(root.get<String>("sortKey")),
            )
            val first = when (sort) {
                AdminCardSort.SET -> emptyList()
                AdminCardSort.NAME -> listOf(builder.asc(root.get<String>("nameSearch")))
                AdminCardSort.POKEDEX -> {
                    val lowestNumber = query.subquery(Int::class.javaObjectType)
                    val card = lowestNumber.correlate(root)
                    lowestNumber.select(builder.min(card.join<Card, Int>("dexNumbers")))
                    listOf(builder.asc(builder.coalesce(lowestNumber, Int.MAX_VALUE)))
                }
            }
            query.orderBy(first + newestSetFirst + builder.asc(root.get<Long>("id")))
        }
        null
    }

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

/** How the admin can sort a list of cards. [param] is the value in the URL. */
enum class AdminCardSort(val param: String) {
    SET("set"),
    NAME("name"),
    POKEDEX("pokedex"),
    ;

    companion object {
        fun fromParam(param: String?): AdminCardSort = entries.firstOrNull { it.param == param } ?: SET
    }
}
