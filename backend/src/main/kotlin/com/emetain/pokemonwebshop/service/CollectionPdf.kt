package com.emetain.pokemonwebshop.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.text.Normalizer

/**
 * The collection export as a PDF (phase 12), for example for insurance: A4 landscape, the same columns as the CSV, the
 * total and the date on top and below, and page numbers. Uses the standard Helvetica of every PDF reader, so nothing is
 * embedded; the few characters it lacks (δ, ☆, ♀ in card names) are written out, see [PdfText].
 */
object CollectionPdf {

    private data class Column(val title: String, val width: Float, val right: Boolean = false)

    private val COLUMNS = listOf(
        Column("Kaart", 145f),
        Column("Spel", 65f),
        Column("Set", 120f),
        Column("Nr.", 40f),
        Column("Variant", 65f),
        Column("Conditie", 60f),
        Column("Aantal", 35f, right = true),
        Column("Prijs p/st", 60f, right = true),
        // Wide enough for "TCGplayer (USD), 20-11-2026".
        Column("Bron", 120f),
        Column("Waarde", 60f, right = true),
    )

    private val PAGE = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
    private const val MARGIN = 36f
    private const val ROW = 13f
    private const val TEXT = 8f
    private const val PADDING = 3f
    private const val FOOTER_SPACE = 30f
    private val INK = Color(12, 31, 74)
    private val SOFT = Color(74, 95, 133)
    private val LINE = Color(216, 227, 245)
    private val TINT = Color(238, 244, 254)

    fun write(table: ExportTable): ByteArray {
        PDDocument().use { document ->
            val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            document.documentInformation.apply {
                title = "Collectie van ${table.ownerName}, ${CollectionExport.formatDate(table.date)}"
                author = "Poketopia"
                creator = "Poketopia"
            }
            val total = CardLabels.money(table.totalCents, table.currency)
            val summary = buildString {
                append("Export van ${CollectionExport.formatDate(table.date)}  ·  ${table.copies} kaarten  ·  Totale waarde $total")
                if (table.unpricedCopies > 0) append("  ·  ${table.unpricedCopies} zonder prijs")
            }

            var page = newPage(document)
            var stream = PDPageContentStream(document, page)
            var y = PAGE.height - MARGIN
            stream.text(bold, 16f, MARGIN, y - 14f, "Collectie van ${table.ownerName}", INK)
            y -= 30f
            stream.text(regular, 9f, MARGIN, y, summary, SOFT)
            y -= 18f
            y = stream.header(bold, y)

            for ((index, line) in table.lines.withIndex()) {
                if (y - ROW < MARGIN + FOOTER_SPACE) {
                    stream.close()
                    page = newPage(document)
                    stream = PDPageContentStream(document, page)
                    y = stream.header(bold, PAGE.height - MARGIN)
                }
                if (index % 2 == 1) stream.fill(MARGIN, y - ROW, COLUMNS.sumOf { it.width.toDouble() }.toFloat(), ROW, TINT)
                val cells = listOf(
                    line.cardName, line.game, line.setName, line.number, line.variant, line.condition,
                    line.quantity.toString(),
                    line.priceCents?.let { CardLabels.money(it, table.currency) } ?: "",
                    line.source,
                    line.valueCents?.let { CardLabels.money(it, table.currency) } ?: "",
                )
                stream.row(regular, y, cells, INK)
                y -= ROW
            }

            if (y - 40f < MARGIN + FOOTER_SPACE) {
                stream.close()
                page = newPage(document)
                stream = PDPageContentStream(document, page)
                y = PAGE.height - MARGIN
            }
            y -= 20f
            stream.text(bold, 11f, MARGIN, y, "Totale waarde: $total", INK)
            y -= 14f
            stream.text(regular, 8.5f, MARGIN, y, "Trendprijzen van Cardmarket en TCGplayer op de genoemde datum: een indicatie van de marktwaarde, geen taxatie.", SOFT)
            stream.close()

            footers(document, regular)
            return ByteArrayOutputStream().also(document::save).toByteArray()
        }
    }

    private fun newPage(document: PDDocument) = PDPage(PAGE).also(document::addPage)

    private fun footers(document: PDDocument, font: PDFont) {
        val count = document.numberOfPages
        document.pages.forEachIndexed { index, page ->
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.text(font, 7.5f, MARGIN, MARGIN - 12f, "Poketopia  ·  poketopia.nl", SOFT)
                val label = "Pagina ${index + 1} van $count"
                val width = font.getStringWidth(label) / 1000 * 7.5f
                stream.text(font, 7.5f, PAGE.width - MARGIN - width, MARGIN - 12f, label, SOFT)
            }
        }
    }

    private fun PDPageContentStream.header(font: PDFont, top: Float): Float {
        fill(MARGIN, top - ROW - 2f, COLUMNS.sumOf { it.width.toDouble() }.toFloat(), ROW + 2f, LINE)
        row(font, top - 1f, COLUMNS.map { it.title }, INK)
        return top - ROW - 2f
    }

    private fun PDPageContentStream.row(font: PDFont, top: Float, cells: List<String>, color: Color) {
        var x = MARGIN
        for ((column, cell) in COLUMNS.zip(cells)) {
            val fitted = PdfText.fit(PdfText.safe(cell, font), font, TEXT, column.width - 2 * PADDING)
            val width = font.getStringWidth(fitted) / 1000 * TEXT
            val left = if (column.right) x + column.width - PADDING - width else x + PADDING
            text(font, TEXT, left, top - ROW + 4f, fitted, color)
            x += column.width
        }
    }

    private fun PDPageContentStream.fill(x: Float, y: Float, width: Float, height: Float, color: Color) {
        setNonStrokingColor(color)
        addRect(x, y, width, height)
        fill()
    }

    private fun PDPageContentStream.text(font: PDFont, size: Float, x: Float, y: Float, text: String, color: Color) {
        beginText()
        setFont(font, size)
        setNonStrokingColor(color)
        newLineAtOffset(x, y)
        showText(PdfText.safe(text, font))
        endText()
    }
}

/** Text for the standard PDF fonts, which only know Western European letters. */
object PdfText {

    /** Card names that use a symbol: δ for Delta Species, the Prism Star, gender signs and the stars of old promos. */
    private val NAMED = mapOf(
        'δ' to "delta", '◇' to "Prism Star", '☆' to "*", '★' to "*", '♀' to "(F)", '♂' to "(M)",
        'α' to "alpha", 'β' to "beta", 'γ' to "gamma", '꞉' to ":",
    )

    fun safe(text: String, font: PDFont): String = buildString {
        for (char in text) {
            when {
                encodes(font, char.toString()) -> append(char)
                char in NAMED -> append(NAMED.getValue(char))
                else -> {
                    // Letters with a mark the font lacks, such as ā and ō, without the mark.
                    val plain = Normalizer.normalize(char.toString(), Normalizer.Form.NFD).filter { it.code < 0x300 || it.code > 0x36f }
                    append(if (plain.isNotEmpty() && encodes(font, plain)) plain else "?")
                }
            }
        }
    }

    /** Shortens [text] with an ellipsis until it fits [width] points. */
    fun fit(text: String, font: PDFont, size: Float, width: Float): String {
        fun widthOf(value: String) = font.getStringWidth(value) / 1000 * size
        if (widthOf(text) <= width) return text
        var end = text.length
        while (end > 0 && widthOf(text.take(end).trimEnd() + "…") > width) end--
        return text.take(end).trimEnd() + "…"
    }

    private fun encodes(font: PDFont, text: String): Boolean = try {
        font.encode(text)
        true
    } catch (exception: IllegalArgumentException) {
        false
    }
}
