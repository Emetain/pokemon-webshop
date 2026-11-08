package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.AccessoryType
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductLanguage
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.ProductDetailDto
import com.emetain.pokemonwebshop.dto.ProductFacetsDto
import com.emetain.pokemonwebshop.dto.ProductListDto
import com.emetain.pokemonwebshop.dto.ProductListRequest
import com.emetain.pokemonwebshop.dto.ProductPageDto
import com.emetain.pokemonwebshop.dto.ProductSummaryDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardPriceRepository
import com.emetain.pokemonwebshop.repository.CardSetRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.repository.ProductSort
import com.emetain.pokemonwebshop.repository.ProductSpecifications
import com.emetain.pokemonwebshop.service.CardOfferService
import com.emetain.pokemonwebshop.service.CatalogText
import com.emetain.pokemonwebshop.service.ProductSlugs
import com.emetain.pokemonwebshop.service.ProductWishlistService
import com.emetain.pokemonwebshop.service.StockAlertService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

internal inline fun <reified E : Enum<E>> enumFilter(value: String?, message: String): E? =
    value.filterValue()?.let { raw ->
        enumValues<E>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: throw ApiException.validation(message)
    }

/** The shop as customers see it: only active products. */
@Component
class ProductOrchestrator(
    private val productRepository: ProductRepository,
    private val cardSetRepository: CardSetRepository,
    private val cardPriceRepository: CardPriceRepository,
    private val wishlistService: ProductWishlistService,
    private val cardOfferService: CardOfferService,
    private val stockAlertService: StockAlertService,
) {

    @Transactional(readOnly = true)
    fun list(request: ProductListRequest, viewer: User?): ProductListDto {
        val game = request.game.filterValue()?.let { Game.fromSlug(it) ?: throw ApiException.notFound() }
        val setIds = when {
            request.set.filterValue() != null -> {
                val setGame = game ?: throw ApiException.validation("Kies eerst een spel.")
                val set = cardSetRepository.findByGameAndCode(setGame, request.set!!) ?: throw ApiException.notFound()
                listOf(set.id)
            }
            request.series.filterValue() != null -> {
                val setGame = game ?: throw ApiException.validation("Kies eerst een spel.")
                seriesSetIds(setGame, request.series!!).ifEmpty { throw ApiException.notFound() }
            }
            else -> null
        }
        val term = request.q.filterValue()?.let(CatalogText::nameSearch)?.takeIf { it.length >= MIN_SEARCH_LENGTH }
        val filters = listOfNotNull(
            ProductSpecifications.withStatus(ProductStatus.ACTIVE),
            ProductSpecifications.ofGame(game),
            ProductSpecifications.ofType(enumFilter<ProductType>(request.type, "Dit soort product kennen we niet.")),
            ProductSpecifications.ofAccessoryType(enumFilter<AccessoryType>(request.accessory, "Dit soort accessoire kennen we niet.")),
            ProductSpecifications.inLanguage(enumFilter<ProductLanguage>(request.language, "Deze taal kennen we niet.")),
            ProductSpecifications.inSets(setIds),
            ProductSpecifications.pricedBetween(request.minPrice?.coerceAtLeast(0), request.maxPrice?.coerceAtLeast(0)),
            ProductSpecifications.inStock(request.inStock),
            ProductSpecifications.preOrder(request.preOrder),
            ProductSpecifications.onSale(request.sale),
            ProductSpecifications.nameContains(term),
            ProductSpecifications.sortedBy(ProductSort.fromParam(request.sort.filterValue())),
        )
        val products = page(filters, request.page, request.size)
        return ProductListDto(results = pageDto(products, viewer), facets = facets(game))
    }

    @Transactional(readOnly = true)
    fun newArrivals(limit: Int, viewer: User?): List<ProductSummaryDto> = highlights(
        listOf(ProductSpecifications.inStock(true)!!, ProductSpecifications.sortedBy(ProductSort.ARRIVED)),
        limit,
        viewer,
    )

    /** Until there are orders, popular means wanted: on wishlists or waiting for stock. Then the newest. */
    @Transactional(readOnly = true)
    fun bestsellers(limit: Int, viewer: User?): List<ProductSummaryDto> = highlights(
        listOf(ProductSpecifications.sortedBy(ProductSort.POPULAR)),
        limit,
        viewer,
    )

    @Transactional(readOnly = true)
    fun detail(slug: String, viewer: User?): ProductDetailDto {
        val product = productRepository.findBySlug(slug)
            ?.takeIf { it.status == ProductStatus.ACTIVE || viewer?.role == Role.ADMIN }
            ?: throw ApiException.notFound("We konden dit product niet vinden.")
        val card = product.card
        val prices = card?.let { cardPriceRepository.findAllByCardIds(listOf(it.id)) }.orEmpty()
        val otherOffers = card?.let { cardOfferService.buyable(listOf(it.id)) }.orEmpty().filter { it.id != product.id }
        val related = related(product)
        val wished = viewer?.let { wishlistService.wished(it.id, listOf(product.id) + related.map(Product::id) + otherOffers.map(Product::id)) }
        return ProductDetailDto.from(
            product = product,
            wished = wished?.contains(product.id),
            prices = prices,
            otherOffers = otherOffers.map { ProductSummaryDto.from(it, wished?.contains(it.id)) },
            related = related.map { ProductSummaryDto.from(it, wished?.contains(it.id)) },
        )
    }

    fun signUpForStock(slug: String, email: String) {
        val product = productRepository.findBySlug(slug)?.takeIf { it.status == ProductStatus.ACTIVE }
            ?: throw ApiException.notFound("We konden dit product niet vinden.")
        retryingOnConflict { stockAlertService.signUp(product.id, email) }
    }

    /** Call this inside a transaction. */
    fun pageDto(products: Page<Product>, viewer: User?): ProductPageDto =
        ProductPageDto.from(products, viewer?.let { wishlistService.wished(it.id, products.content.map(Product::id)) })

    fun page(filters: List<Specification<Product>>, page: Int, size: Int): Page<Product> =
        productRepository.findAll(
            Specification.allOf(filters),
            PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE)),
        )

    /** The sets of one series, by its slug. */
    fun seriesSetIds(game: Game, seriesSlug: String): List<Long> =
        cardSetRepository.findAllNewestFirst(game).filter { ProductSlugs.of(it.series) == seriesSlug }.map { it.id }

    /** Same set first, then the same kind of product of the game. Sold-out products come last. */
    private fun related(product: Product): List<Product> {
        val sort = ProductSpecifications.sortedBy(ProductSort.FEATURED)
        val notThis = Specification<Product> { root, _, builder -> builder.notEqual(root.get<Long>("id"), product.id) }
        val active = ProductSpecifications.withStatus(ProductStatus.ACTIVE)!!
        val sameSet = product.cardSet?.let { set ->
            page(listOf(active, notThis, ProductSpecifications.inSets(listOf(set.id))!!, sort), 0, RELATED).content
        }.orEmpty()
        if (sameSet.size >= RELATED) return sameSet
        val alreadyIn = sameSet.map(Product::id).toSet() + product.id
        val sameKind = page(
            listOfNotNull(
                active,
                notThis,
                ProductSpecifications.ofGame(product.game),
                ProductSpecifications.ofType(product.type),
                sort,
            ),
            0,
            RELATED * 2,
        ).content.filter { it.id !in alreadyIn }
        return (sameSet + sameKind).take(RELATED)
    }

    private fun highlights(filters: List<Specification<Product>>, limit: Int, viewer: User?): List<ProductSummaryDto> {
        val products = page(listOf(ProductSpecifications.withStatus(ProductStatus.ACTIVE)!!) + filters, 0, limit.coerceIn(1, MAX_HIGHLIGHTS))
        return pageDto(products, viewer).items
    }

    private fun facets(game: Game?): ProductFacetsDto {
        val rows = game?.let { productRepository.findFacetsOfGame(it) } ?: productRepository.findFacets()
        return ProductFacetsDto(
            types = rows.map { it.type }.distinct().sorted(),
            accessoryTypes = rows.mapNotNull { it.accessoryType }.distinct().sorted(),
            languages = rows.mapNotNull { it.language }.distinct().sorted(),
        )
    }

    private companion object {
        const val MAX_PAGE_SIZE = 96
        const val MAX_HIGHLIGHTS = 24
        const val RELATED = 8
        const val MIN_SEARCH_LENGTH = 2
    }
}
