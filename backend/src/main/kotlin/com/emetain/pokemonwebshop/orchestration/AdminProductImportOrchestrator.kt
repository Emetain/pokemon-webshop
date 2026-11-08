package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminProductRowDto
import com.emetain.pokemonwebshop.dto.CsvImportResultDto
import com.emetain.pokemonwebshop.dto.ProductImportResultDto
import com.emetain.pokemonwebshop.dto.SetProductTemplateDto
import com.emetain.pokemonwebshop.dto.SetProductsRequest
import com.emetain.pokemonwebshop.dto.SetProductsResultDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.service.ProductCsvService
import com.emetain.pokemonwebshop.service.ProductImportService
import com.emetain.pokemonwebshop.service.ProductService
import com.emetain.pokemonwebshop.service.SetProductTemplates
import com.emetain.pokemonwebshop.service.provider.CatalogHttpException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean

/** Filling the shop in bulk: from Cardmarket's product list, per set, or from a spreadsheet. */
@Component
class AdminProductImportOrchestrator(
    private val importService: ProductImportService,
    private val productService: ProductService,
    private val csvService: ProductCsvService,
) {

    private val importing = AtomicBoolean(false)

    /** One import at a time: a second click while one runs would only make the same products twice. */
    fun importFromCardmarket(gameSlug: String): ProductImportResultDto {
        val game = game(gameSlug)
        if (!importing.compareAndSet(false, true)) {
            throw ApiException(HttpStatus.CONFLICT, "import_running", "Er loopt al een import. Wacht tot die klaar is.")
        }
        try {
            return importService.importFromCardmarket(game)
        } catch (exception: CatalogHttpException) {
            throw ApiException(
                HttpStatus.BAD_GATEWAY,
                "source_unavailable",
                "De productlijst van Cardmarket is nu niet te bereiken. Probeer het later opnieuw.",
            )
        } finally {
            importing.set(false)
        }
    }

    fun setTemplates(gameSlug: String): List<SetProductTemplateDto> =
        SetProductTemplates.of(game(gameSlug)).map { (type, label, usual) -> SetProductTemplateDto(type, label, usual) }

    @Transactional
    fun createForSet(request: SetProductsRequest): SetProductsResultDto {
        val result = productService.createForSet(game(request.game), request.setCode, request.language, request.types)
        return SetProductsResultDto(result.created.map(AdminProductRowDto::from), result.skipped)
    }

    fun exportCsv(): String = csvService.export()

    fun importCsv(bytes: ByteArray, admin: User): CsvImportResultDto = csvService.import(decode(bytes), admin)

    /** UTF-8, or Windows-1252 as older Excel versions save it, so "é" survives either way. */
    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (exception: CharacterCodingException) {
        String(bytes, charset("windows-1252"))
    }

    private fun game(slug: String): Game = Game.fromSlug(slug) ?: throw ApiException.validation("Kies een geldig spel.")
}
