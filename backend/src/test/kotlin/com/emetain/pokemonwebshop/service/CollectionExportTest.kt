package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardCondition
import com.emetain.pokemonwebshop.domain.CardPrice
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.CollectionEntry
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.PriceSource
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Phase 12: the collection export for insurance, as CSV and as PDF. */
class CollectionExportTest {

    private val rates = ExchangeRates(mapOf(Currency.USD to BigDecimal("1.25")))
    private val date = LocalDate.of(2026, 11, 21)
    private val priced = Instant.parse("2026-11-21T03:30:00Z")

    private val surgingSparks = CardSet(Game.POKEMON, "sv08", "SSP", "Surging Sparks", "Scarlet & Violet", LocalDate.of(2024, 11, 8), id = 1)
    private val deltaSpecies = CardSet(Game.POKEMON, "ex13", "DS", "Holon Phantoms", "EX", LocalDate.of(2006, 5, 3), id = 2)
    private val lorcana = CardSet(Game.LORCANA, "1", "TFC", "The First Chapter", "Lorcana", LocalDate.of(2023, 8, 18), id = 3)

    private var nextId = 1L

    private fun card(set: CardSet, name: String, number: String) = Card(
        game = set.game, set = set, externalId = "$name-$number", number = number, name = name,
        nameSearch = name.lowercase(), sortKey = number.padStart(5, '0'), id = nextId++,
    )

    private fun entry(card: Card, variant: String, quantity: Int, condition: CardCondition? = null) =
        CollectionEntry(mock(), card, variant, quantity, condition, Instant.EPOCH)

    private fun price(card: Card, variant: String, cents: Long?, currency: Currency = Currency.EUR, source: PriceSource = PriceSource.CARDMARKET) =
        CardPrice(card, variant, source, currency, cents, null, priced)

    private val pikachu = card(surgingSparks, "Pikachu ex", "238")
    private val squirtle = card(surgingSparks, "Squirtle", "7")
    private val mew = card(deltaSpecies, "Mew δ", "111")
    private val elsa = card(lorcana, "Elsa - Spirit of Winter", "42")

    private fun table(currency: Currency = Currency.EUR) = CollectionExport.table(
        ownerName = "Jay",
        date = date,
        entries = listOf(
            entry(mew, "holo", 1),
            entry(squirtle, "reverse", 3, CardCondition.NEAR_MINT),
            entry(pikachu, "holo", 1, CardCondition.MINT),
            entry(elsa, "normal", 2),
        ),
        prices = listOf(
            price(pikachu, "holo", 285_45),
            price(squirtle, "reverse", 25),
            price(squirtle, "normal", 10),
            price(elsa, "normal", 10_00, Currency.USD, PriceSource.TCGPLAYER),
            price(mew, "holo", null),
        ),
        currency = currency,
        rates = rates,
    )

    @Test
    fun `every line has its card, set, number, variant, condition, quantity, price, source and value`() {
        val lines = table().lines

        // By game, then newest set first, then by number.
        assertEquals(listOf("Squirtle", "Pikachu ex", "Mew δ", "Elsa - Spirit of Winter"), lines.map { it.cardName })
        val squirtleLine = lines.first()
        assertEquals("Pokémon", squirtleLine.game)
        assertEquals("Surging Sparks", squirtleLine.setName)
        assertEquals("7", squirtleLine.number)
        assertEquals("Reverse holo", squirtleLine.variant)
        assertEquals("Near mint", squirtleLine.condition)
        assertEquals(3, squirtleLine.quantity)
        // The reverse holo's own price, never the normal one's.
        assertEquals(25, squirtleLine.priceCents)
        assertEquals(75, squirtleLine.valueCents)
        assertEquals("Cardmarket, 21-11-2026", squirtleLine.source)
    }

    @Test
    fun `dollar prices are converted and say so, and a card without a price counts as unpriced`() {
        val table = table()
        val elsaLine = table.lines.last()

        assertEquals(8_00, elsaLine.priceCents)
        assertEquals(16_00, elsaLine.valueCents)
        assertEquals("TCGplayer (USD), 21-11-2026", elsaLine.source)

        val mewLine = table.lines.single { it.cardName == "Mew δ" }
        assertEquals(null, mewLine.valueCents)
        assertEquals("Geen prijs", mewLine.source)

        assertEquals(285_45 + 75 + 16_00, table.totalCents)
        assertEquals(7, table.copies)
        assertEquals(1, table.unpricedCopies)
    }

    @Test
    fun `the CSV opens as columns in Dutch Excel and ends with the total and the date`() {
        val csv = CollectionExport.csv(table())
        val rows = ProductCsv.parse(csv)

        assertEquals(
            listOf(
                "Spel", "Set", "Setcode", "Kaart", "Nummer", "Variant", "Conditie", "Aantal",
                "Prijs per stuk (EUR)", "Bron", "Waarde (EUR)",
            ),
            rows.first(),
        )
        assertEquals(
            listOf("Pokémon", "Surging Sparks", "SSP", "Pikachu ex", "238", "Holo", "Mint", "1", "285,45", "Cardmarket, 21-11-2026", "285,45"),
            rows[2],
        )
        assertTrue(csv.startsWith("﻿"), "the byte order mark lets Excel read UTF-8")
        assertTrue(rows.contains(listOf("Totale waarde (EUR)", "302,20")))
        assertTrue(rows.contains(listOf("Datum", "21-11-2026")))
        assertTrue(rows.contains(listOf("Kaarten zonder prijs", "1")))
    }

    @Test
    fun `in dollars the amounts and headers are in dollars`() {
        val rows = ProductCsv.parse(CollectionExport.csv(table(Currency.USD)))

        assertEquals("Prijs per stuk (USD)", rows.first()[8])
        assertEquals("10,00", rows.last { it.getOrNull(3) == "Elsa - Spirit of Winter" }[8])
    }

    @Test
    fun `the PDF has the owner, the date, every card and the total`() {
        val bytes = CollectionPdf.write(table())

        Loader.loadPDF(bytes).use { document ->
            val text = PDFTextStripper().getText(document)
            assertTrue("Collectie van Jay" in text, text)
            assertTrue("Export van 21-11-2026" in text, text)
            assertTrue("Pikachu ex" in text, text)
            // Helvetica has no δ: written out instead of dropped.
            assertTrue("Mew delta" in text, text)
            assertTrue("Totale waarde: € 302,20" in text, text)
            assertTrue("Pagina 1 van 1" in text, text)
        }
    }

    @Test
    fun `a large collection continues on more pages, each with its number`() {
        val many = (1..120).map { entry(card(surgingSparks, "Card $it", "$it"), "normal", 1) }
        val table = CollectionExport.table("Jay", date, many, emptyList(), Currency.EUR, rates)

        Loader.loadPDF(CollectionPdf.write(table)).use { document ->
            assertTrue(document.numberOfPages >= 3)
            assertTrue("Pagina ${document.numberOfPages} van ${document.numberOfPages}" in PDFTextStripper().getText(document))
        }
    }

    @Test
    fun `characters the PDF font lacks are written out or simplified`() {
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)

        assertEquals("Nidoran(F) and Pokémon", PdfText.safe("Nidoran♀ and Pokémon", font))
        assertEquals("Ho-Oh *", PdfText.safe("Ho-Oh ☆", font))
        assertEquals("Ryuko", PdfText.safe("Ryūkō", font))
        assertEquals("???", PdfText.safe("ナシッ", font))
        assertTrue(PdfText.fit("A very long card name that doesn't fit", font, 8f, 40f).endsWith("…"))
    }
}
