package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.ReleaseSource
import com.emetain.pokemonwebshop.domain.ReleaseSuggestion
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.ReleaseSuggestionRepository
import com.emetain.pokemonwebshop.repository.SetReleaseRepository
import com.emetain.pokemonwebshop.service.pictures.PictureMatching
import com.emetain.pokemonwebshop.service.pictures.TcgplayerCatalog
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** An upcoming set from one of the sources, before it is stored. */
data class UpcomingSet(val game: Game, val name: String, val releaseDate: LocalDate, val source: ReleaseSource, val setCode: String? = null)

/** Which upcoming sets are worth suggesting. Pure, so it can be tested. */
object ReleaseSuggestionRules {
    /** Tokens, art cards, event promos and prerelease kits come with a set: the set itself is the release. */
    private val SIDE_PRODUCTS = Regex(
        "\\b(tokens?|art series|magicfest|play network|promos?|minigames?|front cards|substitute cards|oversized|pre-?release)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun worthSuggesting(name: String): Boolean = name.isNotBlank() && !SIDE_PRODUCTS.containsMatchIn(name)

    /** One per game and name: the card catalog's date and set code win over TCGplayer's. */
    fun merge(sets: List<UpcomingSet>): List<UpcomingSet> =
        sets.filter { worthSuggesting(it.name) }
            .groupBy { it.game to PictureMatching.setKey(it.name) }
            .map { (_, same) -> same.firstOrNull { it.source == ReleaseSource.CATALOG } ?: same.first() }
}

/**
 * Suggestions for the release calendar. No game has an official release API, so this takes what the shop's sources
 * already know: sets with a future date in the card catalog (Scryfall lists Magic sets months ahead) and TCGplayer's
 * sets with a future date (they appear when pre-orders open). Pokémon sets mostly show up only just before release.
 * The admin adds a suggestion to the calendar or dismisses it.
 */
@Service
class ReleaseSuggestionService(
    private val catalog: TcgplayerCatalog,
    private val cardSetRepository: CardSetRepository,
    private val suggestionRepository: ReleaseSuggestionRepository,
    private val releaseRepository: SetReleaseRepository,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.catalog.release-suggestion-cron}", zone = "Europe/Amsterdam")
    fun refreshNightly() {
        try {
            refresh()
        } catch (exception: Exception) {
            log.warn("Release suggestions not refreshed: {}", exception.message)
        }
    }

    /** Calls TCGplayer once per game, outside a transaction, then stores what is new. Returns how many are new. */
    fun refresh(): Int {
        val today = today()
        val fromCatalog = transactions.execute {
            cardSetRepository.findByReleaseDateAfter(today).map { UpcomingSet(it.game, it.name, it.releaseDate!!, ReleaseSource.CATALOG, it.code) }
        }!!
        val fromTcgplayer = Game.entries.flatMap { game ->
            try {
                catalog.groups(game).filter { it.publishedOn?.isAfter(today) == true }
                    .map { UpcomingSet(game, it.name, it.publishedOn!!, ReleaseSource.TCGPLAYER) }
            } catch (exception: Exception) {
                log.warn("No release suggestions from TCGplayer for {}: {}", game, exception.message)
                emptyList()
            }
        }
        return transactions.execute { store(ReleaseSuggestionRules.merge(fromCatalog + fromTcgplayer)) }!!
    }

    private fun store(sets: List<UpcomingSet>): Int {
        val now = clock.instant()
        var added = 0
        for (set in sets) {
            val key = PictureMatching.setKey(set.name).take(KEY_LENGTH)
            val existing = suggestionRepository.findByGameAndNameKey(set.game, key)
            if (existing == null) {
                suggestionRepository.save(
                    ReleaseSuggestion(set.game, set.name.take(NAME_LENGTH), key, set.releaseDate, set.source, now, now, set.setCode?.take(CODE_LENGTH)),
                )
                added++
            } else if (existing.source == ReleaseSource.TCGPLAYER || set.source == ReleaseSource.CATALOG) {
                // The date can move. A dismissed suggestion stays dismissed.
                existing.name = set.name.take(NAME_LENGTH)
                existing.releaseDate = set.releaseDate
                existing.source = set.source
                existing.setCode = set.setCode?.take(CODE_LENGTH) ?: existing.setCode
                existing.updatedAt = now
            }
        }
        return added
    }

    /** Not dismissed, not out yet, and not on the calendar already (same name or same set). Soonest first. */
    @Transactional(readOnly = true)
    fun pending(): List<ReleaseSuggestion> {
        val releases = releaseRepository.findAllForAdmin()
        val names = releases.map { it.game to PictureMatching.setKey(it.name) }.toSet()
        val sets = releases.mapNotNull { release -> release.cardSet?.let { release.game to it.code } }.toSet()
        return suggestionRepository.findAll()
            .filter { !it.dismissed && !it.releaseDate.isBefore(today()) }
            .filter { (it.game to it.nameKey) !in names && (it.setCode == null || (it.game to it.setCode) !in sets) }
            .sortedWith(compareBy({ it.releaseDate }, { it.name }))
    }

    @Transactional
    fun dismiss(id: Long) {
        val suggestion = suggestionRepository.findById(id).orElseThrow { ApiException.notFound("We konden deze suggestie niet vinden.") }
        suggestion.dismissed = true
        suggestion.updatedAt = clock.instant()
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(ZONE))

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        const val NAME_LENGTH = 150
        const val KEY_LENGTH = 150
        const val CODE_LENGTH = 50
    }
}
