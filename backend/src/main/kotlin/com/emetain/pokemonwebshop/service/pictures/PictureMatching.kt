package com.emetain.pokemonwebshop.service.pictures

import com.emetain.pokemonwebshop.domain.ProductLanguage
import com.emetain.pokemonwebshop.domain.ProductType
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the picture search found for one card or product. */
sealed interface PictureMatch {
    /** Only one product fits: its picture is used right away. */
    data class Sure(val product: TcgProduct) : PictureMatch

    /** More than one could fit, or the name differs: the admin chooses. [score] is 0 to 100. */
    data class Doubtful(val candidates: List<Scored>) : PictureMatch

    data object None : PictureMatch
}

data class Scored(val product: TcgProduct, val score: Int)

/**
 * How cards, products and sets of the shop are matched to TCGplayer's. Pure, so every rule is tested on its own.
 * Tuned on the owner's real catalog on 2026-11-13: of 189 sealed products in 16 sets, 144 matched surely, all
 * correctly; of 262 cards without a picture, 257.
 */
object PictureMatching {

    // --- sets ---------------------------------------------------------------------------------------------------

    /** A set code in front of the name: "SV08: ", "ME06: ", "SWSH09: ", or "SM - " and "XY - ". */
    private val SET_PREFIX = Regex("^([a-z]{0,6}\\d{0,3}[a-z]?\\d*(\\.\\d+)?\\s*:|(sm|xy|bw|swsh|sv|me|ex|dp|hgss)\\s+-)\\s*")
    private val NOT_WORD = Regex("[^a-z0-9]+")
    private const val SET_MIN_SCORE = 70
    private const val CLOSE_DAYS = 60L
    private const val VERY_CLOSE_DAYS = 30L

    /** A set name without case, accents, punctuation and a code in front: "SV08: Surging Sparks" is "surging sparks". */
    fun setName(name: String): String = words(plain(name).replace(SET_PREFIX, "")).joinToString(" ")

    /** The same, but in a fixed order: "Art Series: Strixhaven" and "Strixhaven Art Series" give the same key. */
    fun setKey(name: String): String = setName(name).split(' ').filter { it.isNotEmpty() }.sorted().joinToString(" ")

    /** 100 for the same name, 95 for the same code, lower for a name that contains the other. 0 for no match. */
    fun setScore(code: String, name: String, releaseDate: LocalDate?, group: TcgGroup): Int {
        val ours = setName(name)
        val theirs = setName(group.name)
        if (ours.isEmpty() || theirs.isEmpty()) return 0
        if (ours == theirs) return 100
        val a = ours.split(' ').toSet()
        val b = theirs.split(' ').toSet()
        if (a == b) return 98
        val abbreviation = group.abbreviation?.lowercase()?.replace(NOT_WORD, "")
        if (!abbreviation.isNullOrEmpty() && abbreviation == code.lowercase().replace(NOT_WORD, "")) return 95
        val days = if (releaseDate != null && group.publishedOn != null) abs(ChronoUnit.DAYS.between(releaseDate, group.publishedOn)) else null
        val extra = ((a - b) + (b - a)).size
        if ((a.containsAll(b) || b.containsAll(a)) && days != null && days <= CLOSE_DAYS) return 80 - minOf(10, extra)
        val overlap = (a intersect b).size.toDouble() / (a union b).size
        if (overlap >= 0.6 && days != null && days <= VERY_CLOSE_DAYS) return 70
        return 0
    }

    /** The best TCGplayer set for one of the shop's, or null when none is close enough. A tie goes to the closest date. */
    fun groupFor(code: String, name: String, releaseDate: LocalDate?, groups: List<TcgGroup>): TcgGroup? =
        groups
            .map { it to setScore(code, name, releaseDate, it) }
            .filter { it.second >= SET_MIN_SCORE }
            .maxWithOrNull(
                compareBy<Pair<TcgGroup, Int>> { it.second }
                    .thenByDescending { (group, _) ->
                        if (releaseDate != null && group.publishedOn != null) abs(ChronoUnit.DAYS.between(releaseDate, group.publishedOn)) else Long.MAX_VALUE
                    },
            )?.first

    // --- cards --------------------------------------------------------------------------------------------------

    private val LEADING_ZEROS = Regex("(?<![0-9])0+(?=[0-9])")
    private val NUMBER_SUFFIX = Regex("\\s+-\\s+[A-Za-z]*\\d+[A-Za-z]*/[A-Za-z]*\\d+.*$")
    private val VARIANT = Regex("\\((?!\\d+\\))[^)]*\\)")
    private val PARENTHESES = Regex("\\([^)]*\\)")

    /** "025/191", "25" and "025" are the same number, and so are "SV001/SV122" and "SV001". */
    fun cardNumber(number: String?): String =
        number.orEmpty().substringBefore('/').trim().uppercase().replace(LEADING_ZEROS, "")

    /** The name without a number behind it and without "(Parallel)": "Masquerain - 003/103" is "masquerain". */
    fun cardName(name: String): String =
        words(plain(name.replace(NUMBER_SUFFIX, "").replace(PARENTHESES, " "))).joinToString(" ")

    private fun isVariant(name: String) = VARIANT.containsMatchIn(name.replace(NUMBER_SUFFIX, ""))

    /** Same set and number: sure when only one has the same name. Same number but another name: the admin chooses. */
    fun matchCard(number: String, name: String, products: List<TcgProduct>): PictureMatch {
        val wanted = cardNumber(number)
        if (wanted.isEmpty()) return PictureMatch.None
        val candidates = products.filter { it.number != null && cardNumber(it.number) == wanted }
        if (candidates.isEmpty()) return PictureMatch.None
        val sameName = candidates.filter { cardName(it.name) == cardName(name) }
        val plainOnes = sameName.filterNot { isVariant(it.name) }
        return when {
            sameName.size == 1 -> PictureMatch.Sure(sameName.single())
            plainOnes.size == 1 -> PictureMatch.Sure(plainOnes.single())
            else -> PictureMatch.Doubtful(
                candidates.map { Scored(it, if (it in sameName) 100 else SAME_NUMBER_SCORE) }
                    .sortedByDescending { it.score }
                    .take(MAX_CANDIDATES),
            )
        }
    }

    // --- sealed products ----------------------------------------------------------------------------------------

    private const val SURE_SCORE = 85
    private const val SURE_MARGIN = 15
    private const val MIN_SCORE = 40
    private const val SAME_NUMBER_SCORE = 60
    const val MAX_CANDIDATES = 4

    private val OTHER_LANGUAGE = Regex("non-english|japanese|chinese|korean")
    private val CARDMARKET_CODE = Regex("\\[[A-Z0-9]{3,5}]")
    private val CARD_COUNT = Regex("\\(\\d+\\s*cards?\\)")
    private val COUNT_PACK = Regex("(\\d+)-(pack|card)")

    private val NOISE = setOf(
        "line", "pokemon", "tcg", "the", "of", "and", "a", "cards", "card", "mtg", "magic", "gathering", "one", "piece",
        "disney", "lorcana", "game", "trading", "english", "en",
    )
    private val SYNONYMS = mapOf(
        "halve" to "half", "blisters" to "blister", "boosters" to "booster", "packs" to "pack", "decks" to "deck",
        "tins" to "tin", "boxes" to "box", "single" to "1", "etb" to "elite trainer box",
    )

    /**
     * Words at TCGplayer that make it another product than the shop's, unless the shop's name has the second word
     * too: a case of six boxes, a display of blisters, a set of four packs, a code card.
     */
    private val OTHER_PRODUCT = mapOf("code card" to "code card", " case" to "case", "display" to "display", "set of" to "set", "art bundle" to "art")

    /** Products in other languages get no English picture: the box looks different. */
    fun skipProduct(name: String, language: ProductLanguage?): Boolean =
        (language != null && language != ProductLanguage.EN) || OTHER_LANGUAGE.containsMatchIn(name.lowercase())

    fun productWords(name: String, setNames: List<String>, type: ProductType, ours: Boolean): Set<String> {
        val cleaned = (if (ours) name.replace(CARDMARKET_CODE, " ") else name).let(::plain)
            .replace("fat pack bundle", "bundle")
            .replace("fat pack", "bundle")
            .replace(CARD_COUNT, " ")
            .replace(COUNT_PACK, "$1 $2")
        val dropped = NOISE + setNames.flatMap { setName(it).split(' ') }
        val result = words(cleaned)
            .flatMap { (SYNONYMS[it] ?: it).split(' ') }
            .filter { it.isNotEmpty() && it !in dropped }
            .map { if (!ours && type == ProductType.BOOSTER_BOX && it == "display") "box" else it }
            .toMutableSet()
        if (ours && type in PACK_TYPES && "booster" in result) result += "pack"
        return result
    }

    private val PACK_TYPES = setOf(ProductType.BOOSTER_PACK, ProductType.SLEEVED_BOOSTER)

    /** 0 to 100: the share of words both names have, after the set name and filler words are left out. */
    fun productScore(name: String, type: ProductType, setNames: List<String>, candidate: TcgProduct): Int {
        val theirs = candidate.name.lowercase()
        val mine = name.lowercase()
        val otherProduct = OTHER_PRODUCT.any { (word, needed) ->
            word in theirs && needed !in mine && !(word == "display" && type == ProductType.BOOSTER_BOX)
        }
        if (otherProduct) return 0
        val a = productWords(name, setNames, type, ours = true)
        val b = productWords(candidate.name, setNames, type, ours = false)
        if (a.isEmpty() || b.isEmpty()) return 0
        return ((a intersect b).size * 100.0 / (a union b).size).roundToInt()
    }

    /** Sure when one product clearly fits best. Doubtful when a few come close. */
    fun matchProduct(name: String, type: ProductType, setNames: List<String>, products: List<TcgProduct>): PictureMatch {
        val ranked = products
            .filter { it.number == null }
            .map { Scored(it, productScore(name, type, setNames, it)) }
            .filter { it.score >= MIN_SCORE }
            .sortedByDescending { it.score }
        val best = ranked.firstOrNull() ?: return PictureMatch.None
        val second = ranked.getOrNull(1)?.score ?: 0
        return if (best.score >= SURE_SCORE && best.score - second >= SURE_MARGIN) {
            PictureMatch.Sure(best.product)
        } else {
            PictureMatch.Doubtful(ranked.take(MAX_CANDIDATES))
        }
    }

    // --- shared -------------------------------------------------------------------------------------------------

    /** Lower case, accents off ("Pokémon" is "pokemon"), "&" as "and", and the ♀ and ♂ of Nidoran as f and m. */
    private fun plain(text: String): String =
        Normalizer.normalize(text.replace("♀", " f").replace("♂", " m"), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace("&", " and ")

    private fun words(text: String): List<String> = text.split(NOT_WORD).filter { it.isNotEmpty() }
}
