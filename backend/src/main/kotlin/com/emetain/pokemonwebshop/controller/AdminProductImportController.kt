package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CsvImportResultDto
import com.emetain.pokemonwebshop.dto.ProductImportResultDto
import com.emetain.pokemonwebshop.dto.SetProductTemplateDto
import com.emetain.pokemonwebshop.dto.SetProductsRequest
import com.emetain.pokemonwebshop.dto.SetProductsResultDto
import com.emetain.pokemonwebshop.orchestration.AdminProductImportOrchestrator
import com.emetain.pokemonwebshop.security.AdminOnly
import com.emetain.pokemonwebshop.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@RestController
@RequestMapping("/v1/admin/product-import")
@AdminOnly
class AdminProductImportController(
    private val importOrchestrator: AdminProductImportOrchestrator,
    private val clock: Clock,
) {

    @PostMapping("/cardmarket/{game}")
    fun importFromCardmarket(@PathVariable game: String): ProductImportResultDto =
        importOrchestrator.importFromCardmarket(game)

    @GetMapping("/set-templates/{game}")
    fun setTemplates(@PathVariable game: String): List<SetProductTemplateDto> =
        importOrchestrator.setTemplates(game)

    @PostMapping("/set-products")
    fun createForSet(@Valid @RequestBody request: SetProductsRequest): SetProductsResultDto =
        importOrchestrator.createForSet(request)

    @GetMapping("/csv")
    fun exportCsv(): ResponseEntity<String> {
        val date = LocalDate.now(clock.withZone(ZoneId.of("Europe/Amsterdam")))
        return ResponseEntity.ok()
            .contentType(MediaType("text", "csv", Charsets.UTF_8))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("producten-$date.csv").build().toString(),
            )
            .body(importOrchestrator.exportCsv())
    }

    @PostMapping("/csv")
    fun importCsv(@RequestParam file: MultipartFile, @CurrentUser admin: User): CsvImportResultDto =
        importOrchestrator.importCsv(file.bytes, admin)
}
