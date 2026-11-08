package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.domain.StockMovement
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminProductCardDto
import com.emetain.pokemonwebshop.dto.AdminProductDto
import com.emetain.pokemonwebshop.dto.AdminProductListRequest
import com.emetain.pokemonwebshop.dto.AdminProductPageDto
import com.emetain.pokemonwebshop.dto.AdminProductRowDto
import com.emetain.pokemonwebshop.dto.MarketPriceDto
import com.emetain.pokemonwebshop.dto.MarketSuggestionDto
import com.emetain.pokemonwebshop.dto.ProductRequest
import com.emetain.pokemonwebshop.dto.QuickSingleRequest
import com.emetain.pokemonwebshop.dto.StockAdjustRequest
import com.emetain.pokemonwebshop.dto.StockChangeDto
import com.emetain.pokemonwebshop.dto.StockCountRequest
import com.emetain.pokemonwebshop.dto.StockDto
import com.emetain.pokemonwebshop.dto.StockMovementDto
import com.emetain.pokemonwebshop.dto.StockMovementPageDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.repository.ProductSpecifications
import com.emetain.pokemonwebshop.repository.StockMovementRepository
import com.emetain.pokemonwebshop.service.CatalogText
import com.emetain.pokemonwebshop.service.ExchangeRateService
import com.emetain.pokemonwebshop.service.ProductImageService
import com.emetain.pokemonwebshop.service.ProductService
import com.emetain.pokemonwebshop.service.StockService
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Two stock changes at the same moment: the second one fails on the version of the stock row and is tried once more
 * with the new numbers. That attempt then gives the real answer, such as "uitverkocht".
 */
internal fun <T> retryingOnStockConflict(action: () -> T): T =
    try {
        action()
    } catch (exception: OptimisticLockingFailureException) {
        action()
    }

@Component
class AdminProductOrchestrator(
    private val productService: ProductService,
    private val productImageService: ProductImageService,
    private val stockService: StockService,
    private val productRepository: ProductRepository,
    private val movementRepository: StockMovementRepository,
    private val cardRepository: CardRepository,
    private val cardPriceRepository: CardPriceRepository,
    private val exchangeRateService: ExchangeRateService,
) {

    @Transactional(readOnly = true)
    fun list(request: AdminProductListRequest): AdminProductPageDto {
        val term = request.q.filterValue()?.let(CatalogText::nameSearch)?.takeIf { it.isNotEmpty() }
        val filters = listOfNotNull(
            ProductSpecifications.nameContains(term),
            ProductSpecifications.ofGame(request.game.filterValue()?.let { Game.fromSlug(it) ?: throw ApiException.validation("Onbekend spel.") }),
            ProductSpecifications.ofType(enumFilter<ProductType>(request.type, "Dit soort product kennen we niet.")),
            ProductSpecifications.withStatus(enumFilter<ProductStatus>(request.status, "Deze status kennen we niet.")),
            when (request.stock.filterValue()) {
                "in" -> Specification.not(ProductSpecifications.soldOut())
                "out" -> ProductSpecifications.soldOut()
                "low" -> ProductSpecifications.lowStock()
                else -> null
            },
            ProductSpecifications.imported(
                when (request.source.filterValue()) {
                    "imported" -> true
                    "manual" -> false
                    else -> null
                },
            ),
            if (request.price.filterValue() == "missing") ProductSpecifications.withoutPrice() else null,
        )
        val size = request.size?.takeIf { it in PAGE_SIZES } ?: PAGE_SIZES.first()
        val sort = when (request.sort.filterValue()) {
            "name" -> Sort.by("nameSearch", "id")
            "new" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
            "price" -> Sort.by(Sort.Order.desc("priceCents").nullsLast(), Sort.Order.asc("id"))
            else -> Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))
        }
        val page = PageRequest.of(request.page.coerceAtLeast(0), size, sort)
        return AdminProductPageDto.from(productRepository.findAll(Specification.allOf(filters), page))
    }

    @Transactional(readOnly = true)
    fun get(productId: Long): AdminProductDto = AdminProductDto.from(find(productId))

    @Transactional
    fun create(request: ProductRequest): AdminProductDto = AdminProductDto.from(productService.create(request))

    @Transactional
    fun update(productId: Long, request: ProductRequest): AdminProductDto =
        AdminProductDto.from(productService.update(productId, request))

    @Transactional
    fun duplicate(productId: Long): AdminProductDto = AdminProductDto.from(productService.duplicate(productId))

    @Transactional
    fun archive(productId: Long): AdminProductDto = AdminProductDto.from(productService.archive(productId))

    @Transactional
    fun addImage(productId: Long, bytes: ByteArray): AdminProductDto =
        AdminProductDto.from(productImageService.add(productId, bytes))

    @Transactional
    fun removeImage(productId: Long, index: Int): AdminProductDto =
        AdminProductDto.from(productImageService.remove(productId, index))

    @Transactional
    fun reorderImages(productId: Long, urls: List<String>): AdminProductDto =
        AdminProductDto.from(productImageService.reorder(productId, urls))

    @Transactional(readOnly = true)
    fun movements(productId: Long, page: Int): StockMovementPageDto {
        find(productId)
        val movements = movementRepository.findByProduct(productId, PageRequest.of(page.coerceAtLeast(0), MOVEMENTS_PER_PAGE))
        return StockMovementPageDto.from(movements)
    }

    fun adjustStock(productId: Long, request: StockAdjustRequest, admin: User): StockChangeDto = retryingOnStockConflict {
        stockChange(productId) {
            stockService.adjust(productId, request.change, request.reason, request.note, admin)
        }
    }

    fun countStock(productId: Long, request: StockCountRequest, admin: User): StockChangeDto = retryingOnStockConflict {
        stockChange(productId) { stockService.correctTo(productId, request.onHand, request.note ?: "Geteld", admin) }
    }

    @Transactional(readOnly = true)
    fun lowStock(): List<AdminProductRowDto> {
        val filters = listOf(
            ProductSpecifications.withStatus(ProductStatus.ACTIVE)!!,
            ProductSpecifications.lowStock(),
        )
        val sort = Sort.by("nameSearch", "id")
        return productRepository.findAll(Specification.allOf(filters), PageRequest.of(0, LOW_STOCK_LIMIT, sort))
            .content.map(AdminProductRowDto::from).sortedBy { it.available }
    }

    @Transactional(readOnly = true)
    fun latestMovements(page: Int): StockMovementPageDto =
        StockMovementPageDto.from(movementRepository.findLatest(PageRequest.of(page.coerceAtLeast(0), MOVEMENTS_PER_PAGE)), withProduct = true)

    @Transactional
    fun createSingle(request: QuickSingleRequest, admin: User): AdminProductDto {
        val product = productService.createSingle(request, admin)
        productRepository.flush()
        return AdminProductDto.from(find(product.id))
    }

    /** The market prices of a card in euros, as a suggestion. The admin always chooses the shop price. */
    @Transactional(readOnly = true)
    fun marketSuggestion(cardId: Long): MarketSuggestionDto {
        val card = cardRepository.findWithSetById(cardId) ?: throw ApiException.notFound("We konden deze kaart niet vinden.")
        val rates = exchangeRateService.current()
        val prices = cardPriceRepository.findAllByCardIds(listOf(cardId))
            .sortedBy { CardVariants.displayRank(it.variant) }
            .map { price ->
                MarketPriceDto(
                    variant = price.variant,
                    source = price.source,
                    trendCents = price.trendCents,
                    currency = price.currency,
                    trendEuroCents = price.trendCents?.let { rates.convertCents(it, price.currency, Currency.EUR) },
                    updatedAt = price.updatedAt,
                )
            }
        val existing = productRepository.findAll(
            Specification.allOf(
                listOfNotNull(
                    ProductSpecifications.forCard(cardId),
                    Specification.not(ProductSpecifications.withStatus(ProductStatus.ARCHIVED)),
                ),
            ),
            PageRequest.of(0, LOW_STOCK_LIMIT),
        ).content.map(AdminProductRowDto::from)
        return MarketSuggestionDto(
            card = AdminProductCardDto(
                id = card.id,
                game = card.game.slug,
                setCode = card.set.code,
                setName = card.set.name,
                number = card.number,
                name = card.name,
                imageUrl = card.shownImageSmallUrl,
                variants = CardVariants.displaySorted(card.variants),
            ),
            prices = prices,
            existing = existing,
        )
    }

    private fun stockChange(productId: Long, change: () -> StockMovement?): StockChangeDto {
        val movement = change()
        return StockChangeDto(stock = currentStock(productId), movement = movement?.let { StockMovementDto.from(it) })
    }

    @Transactional(readOnly = true)
    fun currentStock(productId: Long): StockDto = StockDto.from(find(productId))

    private fun find(productId: Long): Product =
        productRepository.findWithDetailsById(productId) ?: throw ApiException.notFound("We konden dit product niet vinden.")

    private companion object {
        /** The choices in the admin's dropdown. The first one is the default. */
        val PAGE_SIZES = listOf(25, 50, 100)
        const val MOVEMENTS_PER_PAGE = 25
        const val LOW_STOCK_LIMIT = 200
    }
}
