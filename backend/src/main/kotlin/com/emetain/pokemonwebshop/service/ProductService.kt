package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductLanguage
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.domain.StockReason
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.ProductRequest
import com.emetain.pokemonwebshop.dto.QuickSingleRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Creating and changing products in admin. Stock is left to [StockService]. */
@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val cardSetRepository: CardSetRepository,
    private val cardRepository: CardRepository,
    private val stockService: StockService,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) {

    @Transactional
    fun create(request: ProductRequest): Product {
        val now = clock.instant()
        val product = Product(
            game = null,
            type = request.type,
            name = "",
            nameSearch = "",
            slug = "",
            createdAt = now,
            updatedAt = now,
        )
        apply(product, request)
        return productRepository.save(product)
    }

    @Transactional
    fun update(productId: Long, request: ProductRequest): Product {
        val product = find(productId)
        apply(product, request)
        product.updatedAt = clock.instant()
        product.editedByAdmin = true
        // A new price or status can make it buyable for people waiting for it.
        events.publishEvent(ProductAvailabilityChanged(product.id))
        return product
    }

    /** A copy as a draft, without stock and without the link to an import. It shares the images of the original. */
    @Transactional
    fun duplicate(productId: Long): Product {
        val original = find(productId)
        val now = clock.instant()
        val name = "${original.name} (kopie)".take(NAME_LENGTH)
        val copy = Product(
            game = original.game,
            type = original.type,
            name = name,
            nameSearch = CatalogText.nameSearch(name),
            slug = ProductSlugs.unique(name, productRepository::existsBySlug),
            createdAt = now,
            updatedAt = now,
            accessoryType = original.accessoryType,
            description = original.description,
            language = original.language,
            cardSet = original.cardSet,
            card = original.card,
            cardVariant = original.cardVariant,
            condition = original.condition,
            gradingCompany = original.gradingCompany,
            grade = original.grade,
            priceCents = original.priceCents,
            salePriceCents = original.salePriceCents,
            status = ProductStatus.DRAFT,
            preOrder = original.preOrder,
            releaseDate = original.releaseDate,
            shipsFrom = original.shipsFrom,
            lowStockThreshold = original.lowStockThreshold,
        )
        copy.images += original.images
        return productRepository.save(copy)
    }

    /** Out of the shop, but kept for the stock log and old orders. */
    @Transactional
    fun archive(productId: Long): Product {
        val product = find(productId)
        product.status = ProductStatus.ARCHIVED
        product.updatedAt = clock.instant()
        product.editedByAdmin = true
        return product
    }

    /** A single from the card catalog in one step, with its first stock bought in. */
    @Transactional
    fun createSingle(request: QuickSingleRequest, admin: User): Product {
        val card = cardRepository.findWithSetById(request.cardId)
            ?: throw ApiException.notFound("We konden deze kaart niet vinden.")
        val variant = request.cardVariant?.takeIf { it.isNotBlank() } ?: CardVariants.default(card.variants)
        val product = create(
            ProductRequest(
                game = card.game.slug,
                type = ProductType.SINGLE,
                name = singleName(card, variant),
                language = request.language,
                cardId = card.id,
                cardVariant = variant,
                condition = request.condition,
                priceCents = request.priceCents,
                status = request.status,
            ),
        )
        if (request.quantity > 0) {
            productRepository.flush()
            stockService.adjust(product.id, request.quantity, StockReason.PURCHASE, "Losse kaart toegevoegd", admin)
        }
        return product
    }

    /**
     * The usual products of a set, such as booster, booster box and Elite Trainer Box, all without stock and
     * without a price. Types the set already has in this language are left out.
     */
    @Transactional
    fun createForSet(game: Game, setCode: String, language: ProductLanguage, types: List<ProductType>): SetProductsResult {
        val set = cardSetRepository.findByGameAndCode(game, setCode)
            ?: throw ApiException.notFound("We konden deze set niet vinden.")
        val invalid = types.filterNot { it.sealed }
        if (invalid.isNotEmpty()) throw ApiException.validation("Alleen verzegelde producten horen bij een set.")
        val existing = productRepository.findTypesInSet(set.id, language).toSet()
        val (skipped, missing) = types.distinct().partition { it in existing }
        val created = missing.map { type ->
            create(
                ProductRequest(
                    game = game.slug,
                    type = type,
                    name = "${set.name} ${SetProductTemplates.label(game, type)}${languageSuffix(language)}",
                    language = language,
                    setCode = set.code,
                    status = ProductStatus.ACTIVE,
                    preOrder = isFuture(set.releaseDate),
                    releaseDate = set.releaseDate,
                    shipsFrom = set.releaseDate.takeIf { isFuture(it) },
                ),
            )
        }
        return SetProductsResult(created, skipped)
    }

    private fun apply(product: Product, request: ProductRequest) {
        val type = request.type
        val game = request.game?.takeIf { it.isNotBlank() }?.let {
            Game.fromSlug(it) ?: throw ApiException.validation("Kies een geldig spel.")
        }
        if (game == null && type != ProductType.ACCESSORY) {
            throw ApiException.validation("Kies het spel van dit product.")
        }

        val card = if (type.forCard) {
            val cardId = request.cardId ?: throw ApiException.validation("Kies de kaart uit de catalogus.")
            cardRepository.findWithSetById(cardId) ?: throw ApiException.validation("We konden deze kaart niet vinden.")
        } else {
            null
        }
        if (card != null && card.game != game) throw ApiException.validation("Deze kaart hoort bij een ander spel.")

        val set = card?.set ?: request.setCode?.takeIf { it.isNotBlank() }?.let { code ->
            val setGame = game ?: throw ApiException.validation("Kies eerst het spel van de set.")
            cardSetRepository.findByGameAndCode(setGame, code) ?: throw ApiException.validation("We konden deze set niet vinden.")
        }

        validatePrices(request.priceCents, request.salePriceCents)
        if (type == ProductType.ACCESSORY && request.accessoryType == null) {
            throw ApiException.validation("Kies wat voor accessoire dit is.")
        }
        if (type == ProductType.SINGLE && request.condition == null) {
            throw ApiException.validation("Kies de conditie van de kaart.")
        }
        if (type == ProductType.GRADED_SLAB && (request.gradingCompany == null || request.grade.isNullOrBlank())) {
            throw ApiException.validation("Kies het gradingbedrijf en vul het cijfer in.")
        }
        if (request.preOrder && request.releaseDate == null) {
            throw ApiException.validation("Een pre-order heeft een releasedatum nodig.")
        }
        val shipsFrom = request.shipsFrom
        if (shipsFrom != null && request.releaseDate != null && shipsFrom.isBefore(request.releaseDate)) {
            throw ApiException.validation("De verzenddatum kan niet voor de releasedatum liggen.")
        }

        val name = request.name.trim()
        if (name.isEmpty()) throw ApiException.validation("Vul een naam in.")
        product.slug = chooseSlug(product, request.slug, name)
        product.name = name
        product.nameSearch = CatalogText.nameSearch(name)
        product.game = game
        product.type = type
        product.accessoryType = request.accessoryType.takeIf { type == ProductType.ACCESSORY }
        product.description = request.description?.replace("\r\n", "\n")?.trim()?.takeIf { it.isNotEmpty() }
        product.language = request.language
        product.cardSet = set
        product.card = card
        product.cardVariant = request.cardVariant?.takeIf { card != null && it.isNotBlank() }?.also { variant ->
            if (variant !in card!!.variants) throw ApiException.validation("Deze kaart heeft die variant niet.")
        }
        product.condition = request.condition.takeIf { type == ProductType.SINGLE }
        product.gradingCompany = request.gradingCompany.takeIf { type == ProductType.GRADED_SLAB }
        product.grade = request.grade?.trim().takeIf { type == ProductType.GRADED_SLAB }
        product.priceCents = request.priceCents
        product.salePriceCents = request.salePriceCents
        product.status = request.status
        product.preOrder = request.preOrder
        product.releaseDate = request.releaseDate
        product.shipsFrom = shipsFrom
        product.lowStockThreshold = request.lowStockThreshold
    }

    private fun chooseSlug(product: Product, requested: String?, name: String): String {
        val wanted = requested?.trim()?.takeIf { it.isNotEmpty() }
        if (wanted == null) {
            // A product keeps its URL when only its name changes, so links people shared keep working.
            return product.slug.ifEmpty { ProductSlugs.unique(name, productRepository::existsBySlug) }
        }
        if (!ProductSlugs.isValid(wanted)) {
            throw ApiException.validation("Een URL-naam heeft alleen kleine letters, cijfers en streepjes.")
        }
        if (wanted != product.slug && productRepository.existsBySlug(wanted)) {
            throw ApiException.validation("Er is al een product met deze URL-naam.")
        }
        return wanted
    }

    private fun validatePrices(price: Long?, sale: Long?) {
        if (price != null && price <= 0) throw ApiException.validation("De prijs moet hoger zijn dan € 0.")
        if (price != null && price > MAX_PRICE_CENTS) throw ApiException.validation("Deze prijs is te hoog.")
        if (sale != null && price == null) throw ApiException.validation("Vul eerst de normale prijs in.")
        if (sale != null && price != null && (sale <= 0 || sale >= price)) {
            throw ApiException.validation("De aanbiedingsprijs moet lager zijn dan de normale prijs.")
        }
    }

    private fun isFuture(date: LocalDate?): Boolean = date != null && date.isAfter(LocalDate.now(clock.withZone(SHOP_ZONE)))

    private fun find(productId: Long): Product =
        productRepository.findWithDetailsById(productId) ?: throw ApiException.notFound("We konden dit product niet vinden.")

    companion object {
        private const val NAME_LENGTH = 255

        /** € 1.000.000, far above any real card. Keeps a typo of a few zeros out. */
        private const val MAX_PRICE_CENTS = 100_000_000L

        val SHOP_ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")

        /** "Charizard (Base Set 4/102)", with the finish when it isn't the usual one. */
        fun singleName(card: Card, variant: String): String {
            val finish = if (variant == CardVariants.default(card.variants)) "" else " ${variantName(variant)}"
            return "${card.name} (${card.set.name} ${card.number})$finish".take(NAME_LENGTH)
        }

        private fun variantName(variant: String) = when (variant) {
            CardVariants.HOLO -> "Holo"
            CardVariants.REVERSE -> "Reverse Holo"
            CardVariants.FIRST_EDITION -> "1st Edition"
            CardVariants.FOIL -> "Foil"
            CardVariants.ETCHED -> "Etched Foil"
            CardVariants.W_PROMO -> "W Promo"
            else -> variant
        }

        private fun languageSuffix(language: ProductLanguage) =
            if (language == ProductLanguage.EN) "" else " (${language.name})"
    }
}

data class SetProductsResult(val created: List<Product>, val skipped: List<ProductType>)

/** What the usual sealed products of a set are called, per game. */
object SetProductTemplates {

    private val LABELS = mapOf(
        Game.POKEMON to linkedMapOf(
            ProductType.BOOSTER_PACK to "Booster",
            ProductType.SLEEVED_BOOSTER to "Sleeved Booster",
            ProductType.BOOSTER_BOX to "Booster Box",
            ProductType.ELITE_TRAINER_BOX to "Elite Trainer Box",
            ProductType.BOOSTER_BUNDLE to "Booster Bundle",
            ProductType.BLISTER to "3-Pack Blister",
            ProductType.TIN to "Tin",
            ProductType.COLLECTION_BOX to "Collection Box",
            ProductType.STARTER_DECK to "Build & Battle Box",
        ),
        Game.MTG to linkedMapOf(
            ProductType.BOOSTER_PACK to "Play Booster",
            ProductType.BOOSTER_BOX to "Play Booster Box",
            ProductType.BOOSTER_BUNDLE to "Bundle",
            ProductType.COLLECTION_BOX to "Collector Booster Box",
            ProductType.STARTER_DECK to "Commander Deck",
        ),
        Game.LORCANA to linkedMapOf(
            ProductType.BOOSTER_PACK to "Booster",
            ProductType.BOOSTER_BOX to "Booster Box",
            ProductType.STARTER_DECK to "Starter Deck",
            ProductType.COLLECTION_BOX to "Gift Set",
            ProductType.TIN to "Illumineer's Trove",
        ),
        Game.ONE_PIECE to linkedMapOf(
            ProductType.BOOSTER_PACK to "Booster",
            ProductType.BOOSTER_BOX to "Booster Box",
            ProductType.STARTER_DECK to "Starter Deck",
        ),
    )

    /** Ticked by default: what nearly every set of the game has. */
    private val USUAL = mapOf(
        Game.POKEMON to setOf(
            ProductType.BOOSTER_PACK,
            ProductType.SLEEVED_BOOSTER,
            ProductType.BOOSTER_BOX,
            ProductType.ELITE_TRAINER_BOX,
            ProductType.BOOSTER_BUNDLE,
        ),
        Game.MTG to setOf(ProductType.BOOSTER_PACK, ProductType.BOOSTER_BOX, ProductType.BOOSTER_BUNDLE),
        Game.LORCANA to setOf(ProductType.BOOSTER_PACK, ProductType.BOOSTER_BOX, ProductType.STARTER_DECK),
        Game.ONE_PIECE to setOf(ProductType.BOOSTER_PACK, ProductType.BOOSTER_BOX),
    )

    fun of(game: Game): List<Triple<ProductType, String, Boolean>> =
        LABELS.getValue(game).map { (type, label) -> Triple(type, label, type in USUAL.getValue(game)) }

    fun label(game: Game, type: ProductType): String =
        LABELS.getValue(game)[type] ?: throw ApiException.validation("Dit soort product hoort niet bij een set van dit spel.")
}
