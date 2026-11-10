package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.AccessoryType
import com.emetain.pokemonwebshop.domain.CardCondition
import com.emetain.pokemonwebshop.domain.CardPrice
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.GradingCompany
import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductLanguage
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.service.CartRules
import com.emetain.pokemonwebshop.service.ProductRules
import org.springframework.data.domain.Page
import java.time.LocalDate

/** The filters and paging of a product list, read from the URL. An empty filter means no filter. */
data class ProductListRequest(
    val game: String? = null,
    val type: String? = null,
    val accessory: String? = null,
    /** The slug of a series, such as `scarlet-violet`. Needs [game]. */
    val series: String? = null,
    /** The code of a set. Needs [game]. */
    val set: String? = null,
    val language: String? = null,
    /** In cents. */
    val minPrice: Long? = null,
    val maxPrice: Long? = null,
    val inStock: Boolean = false,
    val preOrder: Boolean = false,
    val sale: Boolean = false,
    val q: String? = null,
    val sort: String? = null,
    val page: Int = 0,
    val size: Int = 24,
)

/** A product in a list. [imageIsCard] means the image is a card from the catalog: nothing may go on top of it. */
data class ProductSummaryDto(
    val id: Long,
    val slug: String,
    val name: String,
    val game: String?,
    val type: ProductType,
    val accessoryType: AccessoryType?,
    val language: ProductLanguage?,
    val setCode: String?,
    val setName: String?,
    val imageUrl: String?,
    val imageIsCard: Boolean,
    val setLogoUrl: String?,
    val priceCents: Long?,
    val salePriceCents: Long?,
    val shownPriceCents: Long?,
    val discountPercent: Int?,
    val canBuy: Boolean,
    /** Set when only a few are left, for "Laatste X". */
    val lastFew: Int?,
    val preOrder: Boolean,
    val releaseDate: LocalDate?,
    val shipsFrom: LocalDate?,
    val condition: CardCondition?,
    val gradingCompany: GradingCompany?,
    val grade: String?,
    /** Whether the product is on the viewer's wishlist. Null for a visitor who isn't logged in. */
    val wished: Boolean?,
) {
    companion object {
        /** Call this inside a transaction: it reads the stock, set and card. */
        fun from(product: Product, wished: Boolean? = null): ProductSummaryDto {
            val cardImage = product.card?.shownImageSmallUrl
            val ownImage = product.images.firstOrNull()
            val available = product.stock.available
            val canBuy = ProductRules.canBuy(product)
            return ProductSummaryDto(
                id = product.id,
                slug = product.slug,
                name = product.name,
                game = product.game?.slug,
                type = product.type,
                accessoryType = product.accessoryType,
                language = product.language,
                setCode = product.cardSet?.code,
                setName = product.cardSet?.name,
                imageUrl = ownImage ?: cardImage,
                imageIsCard = ownImage == null && cardImage != null,
                setLogoUrl = product.cardSet?.shownLogoUrl,
                priceCents = product.priceCents,
                salePriceCents = product.salePriceCents.takeIf { ProductRules.onSale(product.priceCents, it) },
                shownPriceCents = ProductRules.shownPriceCents(product),
                discountPercent = ProductRules.discountPercent(product.priceCents, product.salePriceCents),
                canBuy = canBuy,
                lastFew = if (canBuy) ProductRules.lastFew(available, product.lowStockThreshold) else null,
                preOrder = product.preOrder,
                releaseDate = product.releaseDate,
                shipsFrom = product.shipsFrom,
                condition = product.condition,
                gradingCompany = product.gradingCompany,
                grade = product.grade,
                wished = wished,
            )
        }
    }
}

data class ProductPageDto(
    val items: List<ProductSummaryDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
) {
    companion object {
        /** [wished] is null for a visitor who isn't logged in. */
        fun from(products: Page<Product>, wished: Set<Long>?) = ProductPageDto(
            items = products.content.map { ProductSummaryDto.from(it, wished?.contains(it.id)) },
            page = products.number,
            size = products.size,
            totalItems = products.totalElements,
            totalPages = products.totalPages,
        )
    }
}

/** The choices that have results in this part of the shop. */
data class ProductFacetsDto(
    val types: List<ProductType>,
    val accessoryTypes: List<AccessoryType>,
    val languages: List<ProductLanguage>,
)

data class ProductListDto(
    val results: ProductPageDto,
    val facets: ProductFacetsDto,
)

/** For singles and slabs: the card in the catalog, with its market prices as a reference. */
data class ProductCardDto(
    val id: Long,
    val game: String,
    val setCode: String,
    val setName: String,
    val number: String,
    val name: String,
    val rarity: String?,
    val imageUrl: String?,
    val prices: List<CardPriceDto>,
)

data class ProductDetailDto(
    val product: ProductSummaryDto,
    /** Only an admin sees a product that isn't active, as a preview. */
    val status: ProductStatus,
    val description: String?,
    val images: List<String>,
    val set: CardSetDto?,
    val card: ProductCardDto?,
    val cardVariant: String?,
    /** Other singles of the same card, such as other conditions. */
    val otherOffers: List<ProductSummaryDto>,
    val related: List<ProductSummaryDto>,
    /** The most one customer can put in the cart now: what is left, up to 99. 0 when it can't be bought. */
    val maxQuantity: Int,
    /** The upcoming or live drop this product is in, so a product that seems sold out says where it went. */
    val drop: ProductDropDto? = null,
) {
    companion object {
        fun from(
            product: Product,
            wished: Boolean?,
            prices: List<CardPrice>,
            otherOffers: List<ProductSummaryDto>,
            related: List<ProductSummaryDto>,
            drop: ProductDropDto? = null,
        ) = ProductDetailDto(
            product = ProductSummaryDto.from(product, wished),
            status = product.status,
            description = product.description,
            images = product.images.toList(),
            set = product.cardSet?.let(CardSetDto::from),
            card = product.card?.let { card ->
                ProductCardDto(
                    id = card.id,
                    game = card.game.slug,
                    setCode = card.set.code,
                    setName = card.set.name,
                    number = card.number,
                    name = card.name,
                    rarity = card.rarity,
                    imageUrl = card.shownImageLargeUrl,
                    prices = prices.map(CardPriceDto::from),
                )
            },
            cardVariant = product.cardVariant,
            otherOffers = otherOffers,
            related = related,
            maxQuantity = if (ProductRules.canBuy(product)) CartRules.maxQuantity(product.stock.available) else 0,
            drop = drop,
        )
    }
}

/** Shown below a card in the catalog when the shop sells it: the cheapest one, and how many there are. */
data class CardOfferDto(
    val slug: String,
    val shownPriceCents: Long,
    val offers: Int,
)

data class StockAlertRequest(
    val email: String = "",
)

/** A series in the shop navigation, newest first. */
data class ShopSeriesDto(
    val slug: String,
    val name: String,
    val setCount: Int,
    val productCount: Long,
    val newestRelease: LocalDate?,
)

data class ShopSetDto(
    val code: String,
    val name: String,
    val releaseDate: LocalDate?,
    val logoUrl: String?,
    val symbolUrl: String?,
    val productCount: Long,
) {
    companion object {
        fun from(set: CardSet, productCount: Long) =
            ShopSetDto(set.code, set.name, set.releaseDate, set.shownLogoUrl, set.symbolUrl, productCount)
    }
}

data class ShopSeriesDetailDto(
    val slug: String,
    val name: String,
    val sets: List<ShopSetDto>,
)
