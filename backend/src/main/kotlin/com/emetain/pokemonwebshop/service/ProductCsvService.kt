package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CsvImportResultDto
import com.emetain.pokemonwebshop.dto.CsvLineErrorDto
import com.emetain.pokemonwebshop.dto.ProductRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.ProductRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Products and stock as a spreadsheet. A row with an `id` or a known `slug` changes that product, any other row
 * makes a new one. A column that isn't in the file leaves that field alone. `on_hand` sets the counted stock
 * with a correction in the stock log, and `reserved` is only there to read.
 */
@Service
class ProductCsvService(
    private val productRepository: ProductRepository,
    private val productService: ProductService,
    private val stockService: StockService,
    private val transactions: TransactionTemplate,
) {

    fun export(): String {
        val rows = mutableListOf(COLUMNS)
        var page = 0
        do {
            val batch = transactions.execute {
                val products = productRepository.findAll(PageRequest.of(page, EXPORT_BATCH, Sort.by("id")))
                products.content.map(::row) to products.hasNext()
            }!!
            rows += batch.first
            page++
        } while (batch.second)
        return ProductCsv.write(rows)
    }

    /** Every row is saved on its own, so one wrong row doesn't stop the rest. */
    fun import(text: String, admin: User): CsvImportResultDto {
        val rows = try {
            ProductCsv.parse(text)
        } catch (exception: CsvFormatException) {
            throw ApiException.validation("Dit CSV-bestand kunnen we niet lezen: ${exception.message}")
        }
        if (rows.isEmpty()) throw ApiException.validation("Dit CSV-bestand is leeg.")
        val header = rows.first().map { it.trim().lowercase() }
        val unknown = header.filter { it.isNotEmpty() && it !in COLUMNS }
        if (unknown.isNotEmpty()) throw ApiException.validation("Onbekende kolommen: ${unknown.joinToString(", ")}.")
        if ("name" !in header && "id" !in header && "slug" !in header) {
            throw ApiException.validation("Het bestand heeft een kolom id, slug of name nodig.")
        }
        if (rows.size - 1 > MAX_ROWS) throw ApiException.validation("Importeer maximaal $MAX_ROWS regels per keer.")

        var created = 0
        var updated = 0
        var unchanged = 0
        var stockChanges = 0
        val errors = mutableListOf<CsvLineErrorDto>()

        rows.drop(1).forEachIndexed { index, cells ->
            val line = index + 2
            val values = header.zip(cells.map(String::trim) + List(maxOf(0, header.size - cells.size)) { "" }).toMap()
            try {
                val outcome = transactions.execute { importRow(values, admin) }!!
                when (outcome.change) {
                    RowChange.CREATED -> created++
                    RowChange.UPDATED -> updated++
                    RowChange.UNCHANGED -> unchanged++
                }
                if (outcome.stockChanged) stockChanges++
            } catch (exception: ApiException) {
                errors += CsvLineErrorDto(line, exception.message)
            } catch (exception: CsvFormatException) {
                errors += CsvLineErrorDto(line, exception.message.orEmpty())
            }
        }
        return CsvImportResultDto(created, updated, unchanged, stockChanges, errors.take(MAX_ERRORS))
    }

    private fun importRow(values: Map<String, String>, admin: User): RowOutcome {
        val existing = values["id"]?.takeIf { it.isNotEmpty() }?.let { id ->
            val productId = id.toLongOrNull() ?: throw CsvFormatException("\"$id\" is geen geldig id.")
            productRepository.findWithDetailsById(productId) ?: throw ApiException.notFound("Er is geen product met id $id.")
        } ?: values["slug"]?.takeIf { it.isNotEmpty() }?.let(productRepository::findBySlug)

        val current = existing?.let(::requestOf) ?: ProductRequest()
        val request = merge(current, values)
        val change: RowChange
        val product = when {
            existing == null -> {
                change = RowChange.CREATED
                productService.create(request)
            }
            request == current -> {
                change = RowChange.UNCHANGED
                existing
            }
            else -> {
                change = RowChange.UPDATED
                productService.update(existing.id, request)
            }
        }
        val onHand = values["on_hand"]?.takeIf { it.isNotEmpty() }?.let {
            it.toIntOrNull() ?: throw CsvFormatException("\"$it\" is geen geldig aantal voor on_hand.")
        }
        val stockChanged = onHand != null && run {
            productRepository.flush()
            stockService.correctTo(product.id, onHand, "CSV-import", admin) != null
        }
        return RowOutcome(change, stockChanged)
    }

    private fun merge(base: ProductRequest, values: Map<String, String>): ProductRequest {
        fun text(column: String, current: String?): String? =
            if (column in values) values.getValue(column).takeIf { it.isNotEmpty() }?.let(ProductCsv::readText) else current

        return base.copy(
            game = text("game", base.game),
            type = enumValue("type", values, base.type) ?: throw CsvFormatException("Vul de kolom type in."),
            accessoryType = enumValue("accessory_type", values, base.accessoryType),
            name = text("name", base.name) ?: "",
            slug = text("slug", base.slug),
            description = text("description", base.description),
            language = enumValue("language", values, base.language),
            setCode = text("set_code", base.setCode),
            cardId = if ("card_id" in values) values.getValue("card_id").takeIf { it.isNotEmpty() }?.let {
                it.toLongOrNull() ?: throw CsvFormatException("\"$it\" is geen geldig card_id.")
            } else base.cardId,
            cardVariant = text("card_variant", base.cardVariant),
            condition = enumValue("condition", values, base.condition),
            gradingCompany = enumValue("grading_company", values, base.gradingCompany),
            grade = text("grade", base.grade),
            priceCents = if ("price" in values) ProductCsv.parseCents(values.getValue("price")) else base.priceCents,
            salePriceCents = if ("sale_price" in values) ProductCsv.parseCents(values.getValue("sale_price")) else base.salePriceCents,
            status = enumValue("status", values, base.status) ?: ProductStatus.DRAFT,
            preOrder = if ("pre_order" in values) yes(values.getValue("pre_order")) else base.preOrder,
            releaseDate = date("release_date", values, base.releaseDate),
            shipsFrom = date("ships_from", values, base.shipsFrom),
            lowStockThreshold = if ("low_stock_threshold" in values && values.getValue("low_stock_threshold").isNotEmpty()) {
                values.getValue("low_stock_threshold").toIntOrNull()
                    ?: throw CsvFormatException("\"${values["low_stock_threshold"]}\" is geen geldig aantal.")
            } else {
                base.lowStockThreshold
            },
        )
    }

    private inline fun <reified E : Enum<E>> enumValue(column: String, values: Map<String, String>, current: E?): E? {
        if (column !in values) return current
        val raw = values.getValue(column).takeIf { it.isNotEmpty() } ?: return null
        return enumValues<E>().firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: throw CsvFormatException("\"$raw\" is geen geldige waarde voor $column.")
    }

    private fun date(column: String, values: Map<String, String>, current: LocalDate?): LocalDate? {
        if (column !in values) return current
        val raw = values.getValue(column).takeIf { it.isNotEmpty() } ?: return null
        return try {
            LocalDate.parse(raw)
        } catch (exception: DateTimeParseException) {
            throw CsvFormatException("\"$raw\" is geen datum. Gebruik de vorm 2026-11-08.")
        }
    }

    private fun yes(value: String) = value.trim().lowercase() in setOf("true", "ja", "1", "yes", "x")

    private fun requestOf(product: Product) = ProductRequest(
        game = product.game?.slug,
        type = product.type,
        accessoryType = product.accessoryType,
        name = product.name,
        slug = product.slug,
        description = product.description,
        language = product.language,
        setCode = product.cardSet?.code,
        cardId = product.card?.id,
        cardVariant = product.cardVariant,
        condition = product.condition,
        gradingCompany = product.gradingCompany,
        grade = product.grade,
        priceCents = product.priceCents,
        salePriceCents = product.salePriceCents,
        status = product.status,
        preOrder = product.preOrder,
        releaseDate = product.releaseDate,
        shipsFrom = product.shipsFrom,
        lowStockThreshold = product.lowStockThreshold,
    )

    private fun row(product: Product): List<String> = listOf(
        product.id.toString(),
        product.slug,
        ProductCsv.safeText(product.name),
        product.game?.slug.orEmpty(),
        product.type.name,
        product.accessoryType?.name.orEmpty(),
        product.language?.name.orEmpty(),
        product.cardSet?.code.orEmpty(),
        product.card?.id?.toString().orEmpty(),
        product.cardVariant.orEmpty(),
        product.condition?.name.orEmpty(),
        product.gradingCompany?.name.orEmpty(),
        ProductCsv.safeText(product.grade),
        ProductCsv.formatCents(product.priceCents),
        ProductCsv.formatCents(product.salePriceCents),
        product.status.name,
        if (product.preOrder) "ja" else "nee",
        product.releaseDate?.toString().orEmpty(),
        product.shipsFrom?.toString().orEmpty(),
        product.lowStockThreshold.toString(),
        product.stock.onHand.toString(),
        product.stock.reserved.toString(),
        ProductCsv.safeText(product.description),
    )

    private enum class RowChange { CREATED, UPDATED, UNCHANGED }

    private data class RowOutcome(val change: RowChange, val stockChanged: Boolean)

    companion object {
        val COLUMNS = listOf(
            "id", "slug", "name", "game", "type", "accessory_type", "language", "set_code", "card_id", "card_variant",
            "condition", "grading_company", "grade", "price", "sale_price", "status", "pre_order", "release_date",
            "ships_from", "low_stock_threshold", "on_hand", "reserved", "description",
        )

        private const val EXPORT_BATCH = 500
        private const val MAX_ROWS = 20_000
        private const val MAX_ERRORS = 200
    }
}
