package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.AccessoryType
import com.emetain.pokemonwebshop.domain.CardCondition
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.GradingCompany
import com.emetain.pokemonwebshop.domain.PriceSource
import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductImportSource
import com.emetain.pokemonwebshop.domain.ProductLanguage
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.ProductType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page
import java.time.Instant
import java.time.LocalDate

/** Everything the admin can set on a product. Prices are in euro cents, including VAT. */
data class ProductRequest(
    /** The game slug, such as `pokemon`. Empty only for accessories that fit every game. */
    val game: String? = null,
    val type: ProductType = ProductType.BOOSTER_BOX,
    val accessoryType: AccessoryType? = null,
    @field:NotBlank(message = "Vul een naam in.")
    @field:Size(max = 255, message = "De naam mag maximaal 255 tekens hebben.")
    val name: String = "",
    /** Empty makes one from the name. */
    val slug: String? = null,
    @field:Size(max = 10000, message = "De beschrijving mag maximaal 10.000 tekens hebben.")
    val description: String? = null,
    val language: ProductLanguage? = null,
    val setCode: String? = null,
    val cardId: Long? = null,
    val cardVariant: String? = null,
    val condition: CardCondition? = null,
    val gradingCompany: GradingCompany? = null,
    @field:Size(max = 20, message = "Het cijfer mag maximaal 20 tekens hebben.")
    val grade: String? = null,
    val priceCents: Long? = null,
    val salePriceCents: Long? = null,
    val status: ProductStatus = ProductStatus.DRAFT,
    val preOrder: Boolean = false,
    val releaseDate: LocalDate? = null,
    val shipsFrom: LocalDate? = null,
    @field:Min(value = 0, message = "De waarschuwing voor lage voorraad kan niet negatief zijn.")
    @field:Max(value = 10000, message = "Kies een lager aantal voor de waarschuwing.")
    val lowStockThreshold: Int = Product.DEFAULT_LOW_STOCK_THRESHOLD,
)

/** A single from the card catalog in one step. [quantity] becomes the first stock, as a purchase. */
data class QuickSingleRequest(
    val cardId: Long = 0,
    val cardVariant: String? = null,
    val condition: CardCondition = CardCondition.NEAR_MINT,
    val language: ProductLanguage = ProductLanguage.EN,
    val priceCents: Long? = null,
    @field:Min(value = 0, message = "Het aantal kan niet negatief zijn.")
    @field:Max(value = 10000, message = "Kies een lager aantal.")
    val quantity: Int = 1,
    val status: ProductStatus = ProductStatus.ACTIVE,
)

/** The filters of the admin product table, read from the URL. An empty filter means no filter. */
data class AdminProductListRequest(
    val q: String? = null,
    val game: String? = null,
    val type: String? = null,
    val status: String? = null,
    /** `in`, `out` or `low`. */
    val stock: String? = null,
    /** `imported` or `manual`. */
    val source: String? = null,
    /** `missing` for products without a price. */
    val price: String? = null,
    val sort: String? = null,
    val page: Int = 0,
    val size: Int? = null,
)

data class AdminProductRowDto(
    val id: Long,
    val slug: String,
    val name: String,
    val game: String?,
    val type: ProductType,
    val accessoryType: AccessoryType?,
    val language: ProductLanguage?,
    val setName: String?,
    val status: ProductStatus,
    val priceCents: Long?,
    val salePriceCents: Long?,
    val onHand: Int,
    val reserved: Int,
    val available: Int,
    val lowStockThreshold: Int,
    val imageUrl: String?,
    val preOrder: Boolean,
    val importSource: ProductImportSource?,
    val editedByAdmin: Boolean,
    val updatedAt: Instant,
) {
    companion object {
        fun from(product: Product) = AdminProductRowDto(
            id = product.id,
            slug = product.slug,
            name = product.name,
            game = product.game?.slug,
            type = product.type,
            accessoryType = product.accessoryType,
            language = product.language,
            setName = product.cardSet?.name,
            status = product.status,
            priceCents = product.priceCents,
            salePriceCents = product.salePriceCents,
            onHand = product.stock.onHand,
            reserved = product.stock.reserved,
            available = product.stock.available,
            lowStockThreshold = product.lowStockThreshold,
            imageUrl = product.images.firstOrNull() ?: product.card?.shownImageSmallUrl,
            preOrder = product.preOrder,
            importSource = product.importSource,
            editedByAdmin = product.editedByAdmin,
            updatedAt = product.updatedAt,
        )
    }
}

data class AdminProductPageDto(
    val items: List<AdminProductRowDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
) {
    companion object {
        fun from(products: Page<Product>) = AdminProductPageDto(
            items = products.content.map(AdminProductRowDto::from),
            page = products.number,
            size = products.size,
            totalItems = products.totalElements,
            totalPages = products.totalPages,
        )
    }
}

/** The card of a single or slab, as the admin form shows it. */
data class AdminProductCardDto(
    val id: Long,
    val game: String,
    val setCode: String,
    val setName: String,
    val number: String,
    val name: String,
    val imageUrl: String?,
    val variants: List<String>,
)

data class AdminProductDto(
    val id: Long,
    val slug: String,
    val game: String?,
    val type: ProductType,
    val accessoryType: AccessoryType?,
    val name: String,
    val description: String?,
    val language: ProductLanguage?,
    val setCode: String?,
    val setName: String?,
    val card: AdminProductCardDto?,
    val cardVariant: String?,
    val condition: CardCondition?,
    val gradingCompany: GradingCompany?,
    val grade: String?,
    val priceCents: Long?,
    val salePriceCents: Long?,
    val images: List<String>,
    val status: ProductStatus,
    val preOrder: Boolean,
    val releaseDate: LocalDate?,
    val shipsFrom: LocalDate?,
    val lowStockThreshold: Int,
    val stock: StockDto,
    val importSource: ProductImportSource?,
    val importKey: String?,
    val editedByAdmin: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        /** Call this inside a transaction: it reads the stock, set and card. */
        fun from(product: Product) = AdminProductDto(
            id = product.id,
            slug = product.slug,
            game = product.game?.slug,
            type = product.type,
            accessoryType = product.accessoryType,
            name = product.name,
            description = product.description,
            language = product.language,
            setCode = product.cardSet?.code,
            setName = product.cardSet?.name,
            card = product.card?.let { card ->
                AdminProductCardDto(
                    id = card.id,
                    game = card.game.slug,
                    setCode = card.set.code,
                    setName = card.set.name,
                    number = card.number,
                    name = card.name,
                    imageUrl = card.shownImageSmallUrl,
                    variants = com.emetain.pokemonwebshop.domain.CardVariants.displaySorted(card.variants),
                )
            },
            cardVariant = product.cardVariant,
            condition = product.condition,
            gradingCompany = product.gradingCompany,
            grade = product.grade,
            priceCents = product.priceCents,
            salePriceCents = product.salePriceCents,
            images = product.images.toList(),
            status = product.status,
            preOrder = product.preOrder,
            releaseDate = product.releaseDate,
            shipsFrom = product.shipsFrom,
            lowStockThreshold = product.lowStockThreshold,
            stock = StockDto.from(product),
            importSource = product.importSource,
            importKey = product.importKey,
            editedByAdmin = product.editedByAdmin,
            createdAt = product.createdAt,
            updatedAt = product.updatedAt,
        )
    }
}

data class ImageOrderRequest(
    val urls: List<String> = emptyList(),
)

/** A market price of a card, converted to euros, as a suggestion for the shop price. Never used by itself. */
data class MarketPriceDto(
    val variant: String,
    val source: PriceSource,
    val trendCents: Long?,
    val currency: Currency,
    val trendEuroCents: Long?,
    val updatedAt: Instant,
)

data class MarketSuggestionDto(
    val card: AdminProductCardDto,
    val prices: List<MarketPriceDto>,
    /** The singles this card already has in the shop, so the admin doesn't add one twice. */
    val existing: List<AdminProductRowDto>,
)

/** The usual products of a set in one go, for when there is no product list to import. */
data class SetProductsRequest(
    val game: String = "",
    val setCode: String = "",
    val language: ProductLanguage = ProductLanguage.EN,
    @field:NotEmpty(message = "Kies minstens één soort product.")
    val types: List<ProductType> = emptyList(),
)

data class SetProductTemplateDto(
    val type: ProductType,
    /** The name after the set name, such as "Elite Trainer Box". */
    val label: String,
    val usual: Boolean,
)

data class SetProductsResultDto(
    val created: List<AdminProductRowDto>,
    /** Types the set already had in this language, so they were left out. */
    val skipped: List<ProductType>,
)

/** What a CSV import did. Line numbers count the header as line 1. */
data class CsvImportResultDto(
    val created: Int,
    val updated: Int,
    val unchanged: Int,
    val stockChanges: Int,
    val errors: List<CsvLineErrorDto>,
)

data class CsvLineErrorDto(val line: Int, val message: String)

/** What an import from a product list did. */
data class ProductImportResultDto(
    val game: String,
    val source: ProductImportSource,
    val inList: Int,
    val created: Int,
    val updated: Int,
    val unchanged: Int,
    /** Products the admin edited: an import leaves them alone. */
    val keptEdited: Int,
    /** Not a product type the shop sells, such as coins or complete card sets. */
    val skippedType: Int,
    /** Not sold in Europe, a case of several boxes, or a Japanese or Chinese edition. */
    val skippedMarket: Int,
    /** No set in our catalog: often an edition from outside Europe, or a set the card sync doesn't have yet. */
    val skippedNoSet: Int,
)
