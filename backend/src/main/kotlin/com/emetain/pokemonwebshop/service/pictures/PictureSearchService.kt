package com.emetain.pokemonwebshop.service.pictures

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.PictureCandidate
import com.emetain.pokemonwebshop.domain.PictureSuggestion
import com.emetain.pokemonwebshop.domain.ProductLanguage
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.domain.SuggestionStatus
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.PictureSuggestionRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** How far the picture search of one game is. Replaced as a whole on every step, so a reader never sees half of one. */
data class PictureSearchStatus(
    val game: Game,
    val running: Boolean,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val setsTotal: Int = 0,
    val setsDone: Int = 0,
    /** Sets of the shop that have no counterpart at TCGplayer: their cards and products stay as they are. */
    val setsNotFound: Int = 0,
    val cardsFilled: Int = 0,
    val productsFilled: Int = 0,
    val suggested: Int = 0,
    val notFound: Int = 0,
    /** Products in another language: they get no English picture. */
    val skipped: Int = 0,
    val error: String? = null,
)

private data class CardTarget(val id: Long, val number: String, val name: String)

private data class ProductTarget(val id: Long, val name: String, val type: ProductType, val language: ProductLanguage?)

private data class SetTargets(
    val code: String,
    val name: String,
    val releaseDate: LocalDate?,
    val cards: List<CardTarget>,
    val products: List<ProductTarget>,
)

/**
 * Finds pictures at TCGplayer for cards and sealed products without one. Sure matches get the picture right away;
 * doubtful ones become a [PictureSuggestion] for the admin. A card's picture goes into its `found` fields, a
 * product's into its pictures. Runs in the background, one search per game at a time, and calls tcgcsv.com once
 * for the sets and once per set that needs something.
 */
@Service
class PictureSearchService(
    private val catalog: TcgplayerCatalog,
    private val cardRepository: CardRepository,
    private val productRepository: ProductRepository,
    private val suggestionRepository: PictureSuggestionRepository,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // A private pool, like the card sync: an Executor bean would replace the one the @Async mails use.
    private val executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("picture-search-", 1).daemon().factory())
    private val statuses = ConcurrentHashMap<Game, PictureSearchStatus>()

    fun status(game: Game): PictureSearchStatus? = statuses[game]

    /** Returns right away. The search continues in the background. */
    fun start(game: Game): PictureSearchStatus {
        val started = PictureSearchStatus(game, running = true, startedAt = clock.instant())
        val previous = statuses.putIfAbsent(game, started)
        if (previous != null) {
            if (previous.running) throw ApiException(HttpStatus.CONFLICT, "picture_search_running", "Het zoeken naar afbeeldingen loopt al voor dit spel.")
            if (!statuses.replace(game, previous, started)) throw ApiException(HttpStatus.CONFLICT, "picture_search_running", "Het zoeken naar afbeeldingen loopt al voor dit spel.")
        }
        executor.execute { run(game) }
        return started
    }

    /** The search itself. The background runs this; tests call it straight away. */
    fun run(game: Game): PictureSearchStatus {
        var status = statuses[game]?.takeIf { it.running } ?: PictureSearchStatus(game, running = true, startedAt = clock.instant())
        fun update(next: PictureSearchStatus) {
            status = next
            statuses[game] = next
        }
        update(status)
        try {
            val targets = targets(game)
            update(status.copy(setsTotal = targets.size))
            val groups = if (targets.isEmpty()) emptyList() else catalog.groups(game)
            for (set in targets) {
                val group = PictureMatching.groupFor(set.code, set.name, set.releaseDate, groups)
                if (group == null) {
                    update(status.copy(setsDone = status.setsDone + 1, setsNotFound = status.setsNotFound + 1, notFound = status.notFound + set.cards.size + set.products.size))
                    continue
                }
                val products = catalog.products(game, group.id)
                val result = transactions.execute { apply(game, set, listOf(set.name, group.name), products) }!!
                update(
                    status.copy(
                        setsDone = status.setsDone + 1,
                        cardsFilled = status.cardsFilled + result.cardsFilled,
                        productsFilled = status.productsFilled + result.productsFilled,
                        suggested = status.suggested + result.suggested,
                        notFound = status.notFound + result.notFound,
                        skipped = status.skipped + result.skipped,
                    ),
                )
            }
            log.info("Picture search for {}: {}", game, status)
            update(status.copy(running = false, finishedAt = clock.instant()))
        } catch (exception: Exception) {
            log.error("Picture search for {} failed", game, exception)
            update(status.copy(running = false, finishedAt = clock.instant(), error = exception.message ?: exception.toString()))
        }
        return status
    }

    private data class SetResult(val cardsFilled: Int = 0, val productsFilled: Int = 0, val suggested: Int = 0, val notFound: Int = 0, val skipped: Int = 0)

    /** What has no picture yet, per set: without the ones that already have a suggestion, open or rejected. */
    private fun targets(game: Game): List<SetTargets> = transactions.execute {
        val cardsDone = suggestionRepository.findCardIdsWithSuggestion(game).toSet()
        val productsDone = suggestionRepository.findProductIdsWithSuggestion(game).toSet()
        val cards = cardRepository.findWithoutPicture(game).filter { it.id !in cardsDone }
        val products = productRepository.findSealedWithoutPicture(game).filter { it.id !in productsDone && it.cardSet != null }
        val sets = (cards.map { it.set } + products.map { it.cardSet!! }).distinctBy { it.id }
        sets.map { set ->
            SetTargets(
                code = set.code,
                name = set.name,
                releaseDate = set.releaseDate,
                cards = cards.filter { it.set.id == set.id }.map { CardTarget(it.id, it.number, it.name) },
                products = products.filter { it.cardSet!!.id == set.id }.map { ProductTarget(it.id, it.name, it.type, it.language) },
            )
        }
    }!!

    private fun apply(game: Game, set: SetTargets, setNames: List<String>, products: List<TcgProduct>): SetResult {
        var result = SetResult()
        val now = clock.instant()
        for (target in set.cards) {
            val card = cardRepository.findById(target.id).orElse(null)?.takeIf { !it.hasPicture } ?: continue
            result = when (val match = PictureMatching.matchCard(target.number, target.name, products)) {
                is PictureMatch.Sure -> {
                    card.foundImageSmallUrl = TcgplayerImages.small(match.product.id)
                    card.foundImageLargeUrl = TcgplayerImages.large(match.product.id)
                    result.copy(cardsFilled = result.cardsFilled + 1)
                }
                is PictureMatch.Doubtful -> {
                    suggestionRepository.save(PictureSuggestion(game, now, card = card, candidates = candidates(match)))
                    result.copy(suggested = result.suggested + 1)
                }
                PictureMatch.None -> result.copy(notFound = result.notFound + 1)
            }
        }
        for (target in set.products) {
            if (PictureMatching.skipProduct(target.name, target.language)) {
                result = result.copy(skipped = result.skipped + 1)
                continue
            }
            val product = productRepository.findById(target.id).orElse(null)?.takeIf { it.images.isEmpty() } ?: continue
            result = when (val match = PictureMatching.matchProduct(target.name, target.type, setNames, products)) {
                is PictureMatch.Sure -> {
                    product.images += TcgplayerImages.large(match.product.id)
                    result.copy(productsFilled = result.productsFilled + 1)
                }
                is PictureMatch.Doubtful -> {
                    suggestionRepository.save(PictureSuggestion(game, now, product = product, candidates = candidates(match)))
                    result.copy(suggested = result.suggested + 1)
                }
                PictureMatch.None -> result.copy(notFound = result.notFound + 1)
            }
        }
        return result
    }

    private fun candidates(match: PictureMatch.Doubtful) =
        match.candidates.map { PictureCandidate(it.product.id, it.product.name.take(NAME_LENGTH), it.score) }.toMutableList()

    @Transactional(readOnly = true)
    fun pending(game: Game?, page: Int): Page<PictureSuggestion> =
        suggestionRepository.findByStatus(SuggestionStatus.PENDING, game, PageRequest.of(page.coerceAtLeast(0), PAGE_SIZE)).also { result ->
            result.content.forEach { it.candidates.size }
        }

    /** The admin's choice: this candidate's picture goes on the card or product. */
    @Transactional
    fun accept(suggestionId: Long, tcgplayerId: Long): PictureSuggestion {
        val suggestion = open(suggestionId)
        if (suggestion.candidates.none { it.tcgplayerId == tcgplayerId }) throw ApiException.validation("Kies een van de voorgestelde afbeeldingen.")
        suggestion.card?.let {
            it.foundImageSmallUrl = TcgplayerImages.small(tcgplayerId)
            it.foundImageLargeUrl = TcgplayerImages.large(tcgplayerId)
        }
        suggestion.product?.let { product ->
            val url = TcgplayerImages.large(tcgplayerId)
            if (url !in product.images) product.images += url
        }
        suggestion.status = SuggestionStatus.ACCEPTED
        suggestion.decidedAt = clock.instant()
        return suggestion
    }

    /** None of the candidates fit. The next search leaves this card or product alone. */
    @Transactional
    fun reject(suggestionId: Long): PictureSuggestion {
        val suggestion = open(suggestionId)
        suggestion.status = SuggestionStatus.REJECTED
        suggestion.decidedAt = clock.instant()
        return suggestion
    }

    private fun open(suggestionId: Long): PictureSuggestion {
        val suggestion = suggestionRepository.findWithCandidates(suggestionId) ?: throw ApiException.notFound("We konden deze suggestie niet vinden.")
        if (suggestion.status != SuggestionStatus.PENDING) throw ApiException(HttpStatus.CONFLICT, "suggestion_decided", "Over deze suggestie is al beslist.")
        return suggestion
    }

    @PreDestroy
    fun stop() {
        executor.shutdownNow()
    }

    private companion object {
        const val NAME_LENGTH = 300
        const val PAGE_SIZE = 20
    }
}
