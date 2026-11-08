package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductImportSource
import com.emetain.pokemonwebshop.domain.ProductLanguage
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.dto.ProductImportResultDto
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.service.provider.CardmarketProduct
import com.emetain.pokemonwebshop.service.provider.CardmarketProductMapper
import com.emetain.pokemonwebshop.service.provider.CardmarketProductProvider
import com.emetain.pokemonwebshop.service.provider.CatalogSetName
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.LocalDate

/**
 * Fills the shop with every sealed product of the sets in our catalog that are still printed, as "Uitverkocht" until
 * there is stock.
 * An import never touches stock, prices or a product the admin edited, so it can run again safely.
 */
@Service
class ProductImportService(
    private val provider: CardmarketProductProvider,
    private val productRepository: ProductRepository,
    private val cardSetRepository: CardSetRepository,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** The download happens outside the transaction, so a slow source never holds the database. */
    fun importFromCardmarket(game: Game): ProductImportResultDto {
        val products = provider.products(game)
        val result = transactions.execute { apply(game, products) }!!
        log.info("Product import for {}: {}", game, result)
        return result
    }

    @Transactional
    fun apply(game: Game, products: List<CardmarketProduct>): ProductImportResultDto {
        val sets = cardSetRepository.findAllNewestFirst(game)
        val plan = CardmarketProductMapper.plan(products, sets.map { CatalogSetName(it.id, it.name) })
        val setsById = sets.associateBy { it.id }
        val existing = plan.products.map { it.product.id.toString() }.chunked(QUERY_CHUNK).flatMap {
            productRepository.findAllByImportSourceAndImportKeyIn(SOURCE, it)
        }.associateBy { it.importKey }
        val slugs = productRepository.findAllSlugs().toHashSet()
        val today = LocalDate.now(clock.withZone(ProductService.SHOP_ZONE))
        val now = clock.instant()

        var created = 0
        var updated = 0
        var unchanged = 0
        var keptEdited = 0
        var skippedOutOfPrint = 0
        for (planned in plan.products) {
            val set = setsById.getValue(planned.setId)
            // All sets go into the plan, so that a product of an old set can't end up in a newer set with a similar name.
            if (!set.inPrint) {
                skippedOutOfPrint++
                continue
            }
            val release = set.releaseDate
            val preOrder = release != null && release.isAfter(today)
            val shipsFrom = release.takeIf { preOrder }
            val name = planned.product.name.take(NAME_LENGTH)
            val product = existing[planned.product.id.toString()]
            when {
                product == null -> {
                    val slug = ProductSlugs.unique(name) { it in slugs }
                    slugs += slug
                    productRepository.save(
                        Product(
                            game = game,
                            type = planned.type,
                            name = name,
                            nameSearch = CatalogText.nameSearch(name),
                            slug = slug,
                            createdAt = now,
                            updatedAt = now,
                            language = ProductLanguage.EN,
                            cardSet = set,
                            status = ProductStatus.ACTIVE,
                            preOrder = preOrder,
                            releaseDate = release,
                            shipsFrom = shipsFrom,
                            importSource = SOURCE,
                            importKey = planned.product.id.toString(),
                        ),
                    )
                    created++
                }
                product.editedByAdmin -> keptEdited++
                product.name == name && product.type == planned.type && product.cardSet?.id == set.id &&
                    product.releaseDate == release && product.preOrder == preOrder && product.shipsFrom == shipsFrom -> unchanged++
                else -> {
                    product.name = name
                    product.nameSearch = CatalogText.nameSearch(name)
                    product.type = planned.type
                    product.cardSet = set
                    product.releaseDate = release
                    product.preOrder = preOrder
                    product.shipsFrom = shipsFrom
                    product.updatedAt = now
                    updated++
                }
            }
        }
        return ProductImportResultDto(
            game = game.slug,
            source = SOURCE,
            inList = plan.inList,
            created = created,
            updated = updated,
            unchanged = unchanged,
            keptEdited = keptEdited,
            skippedType = plan.skippedType,
            skippedMarket = plan.skippedMarket,
            skippedNoSet = plan.skippedNoSet,
            skippedOutOfPrint = skippedOutOfPrint,
        )
    }

    private companion object {
        val SOURCE = ProductImportSource.CARDMARKET
        const val NAME_LENGTH = 255
        const val QUERY_CHUNK = 1000
    }
}
