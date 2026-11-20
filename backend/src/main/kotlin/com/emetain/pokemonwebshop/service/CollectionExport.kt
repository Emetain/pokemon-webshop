package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.CardPrice
import com.emetain.pokemonwebshop.domain.CollectionEntry
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.PriceSource
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One line of the export: one variant of a card in the collection, with its price per copy and the line's value. */
data class ExportLine(
    val game: String,
    val setName: String,
    val setCode: String,
    val cardName: String,
    val number: String,
    val variant: String,
    val condition: String,
    val quantity: Int,
    /** Per copy, in the export's currency; null without a price or an exchange rate. */
    val priceCents: Long?,
    /** Where the price comes from and on which day, for example "Cardmarket, 20-11-2026". */
    val source: String,
    val valueCents: Long?,
)

/** A whole collection on one day, in one currency: what an insurer asks for. */
data class ExportTable(
    val ownerName: String,
    val date: LocalDate,
    val currency: Currency,
    val lines: List<ExportLine>,
) {
    val totalCents: Long get() = lines.sumOf { it.valueCents ?: 0 }
    val copies: Long get() = lines.sumOf { it.quantity.toLong() }
    val unpricedCopies: Long get() = lines.filter { it.valueCents == null }.sumOf { it.quantity.toLong() }
}

/**
 * The collection export of phase 12 (Premium): card, set, number, variant, condition, quantity, price and source, with
 * the total value and the date. Pure, so the tests can check it line by line; [CollectionPdf] draws the same table.
 */
object CollectionExport {

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val SHOP_ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")

    fun table(
        ownerName: String,
        date: LocalDate,
        entries: List<CollectionEntry>,
        prices: List<CardPrice>,
        currency: Currency,
        rates: ExchangeRates,
    ): ExportTable {
        val byVariant = prices.filter { it.trendCents != null }
            .sortedBy { it.source }
            .groupBy { it.card.id to it.variant }
            .mapValues { it.value.first() }
        val lines = entries
            .sortedWith(
                compareBy<CollectionEntry> { it.card.game.ordinal }
                    .thenByDescending { it.card.set.releaseDate }
                    .thenBy { it.card.set.name }
                    .thenBy { it.card.sortKey }
                    .thenBy { it.variant },
            )
            .map { entry ->
                val price = byVariant[entry.card.id to entry.variant]
                val perCopy = price?.let { rates.convertCents(it.trendCents!!, it.currency, currency) }
                ExportLine(
                    game = CardLabels.game(entry.card.game),
                    setName = entry.card.set.name,
                    setCode = entry.card.set.code,
                    cardName = entry.card.name,
                    number = entry.card.number,
                    variant = CardLabels.variant(entry.variant),
                    condition = CardLabels.condition(entry.condition),
                    quantity = entry.quantity,
                    priceCents = perCopy,
                    source = price?.let { source(it, currency, perCopy != null) } ?: "Geen prijs",
                    valueCents = perCopy?.let { it * entry.quantity },
                )
            }
        return ExportTable(ownerName, date, currency, lines)
    }

    /** Semicolons and decimal commas, so Dutch Excel opens it as columns. Amounts are in the header's currency. */
    fun csv(table: ExportTable): String {
        val code = table.currency.name
        val header = listOf(
            "Spel", "Set", "Setcode", "Kaart", "Nummer", "Variant", "Conditie", "Aantal",
            "Prijs per stuk ($code)", "Bron", "Waarde ($code)",
        )
        val rows = table.lines.map { line ->
            // Text from the card sources that starts like a formula is shown, never run (ProductCsv.safeText).
            listOf(
                line.game, ProductCsv.safeText(line.setName), ProductCsv.safeText(line.setCode),
                ProductCsv.safeText(line.cardName), ProductCsv.safeText(line.number), line.variant, line.condition,
                line.quantity.toString(), line.priceCents?.let(CardLabels::amount).orEmpty(), line.source,
                line.valueCents?.let(CardLabels::amount).orEmpty(),
            )
        }
        val footer = buildList {
            add(emptyList())
            add(listOf("Totale waarde ($code)", CardLabels.amount(table.totalCents)))
            add(listOf("Aantal kaarten", table.copies.toString()))
            if (table.unpricedCopies > 0) add(listOf("Kaarten zonder prijs", table.unpricedCopies.toString()))
            add(listOf("Datum", DATE.format(table.date)))
            add(listOf("Bron", "Poketopia; trendprijzen van Cardmarket en TCGplayer, een indicatie van de marktwaarde"))
        }
        return ProductCsv.write(listOf(header) + rows + footer)
    }

    fun formatDate(date: LocalDate): String = DATE.format(date)

    /** "Cardmarket, 20-11-2026", and for a converted price "TCGplayer (USD), 20-11-2026". */
    private fun source(price: CardPrice, currency: Currency, converted: Boolean): String {
        val name = when (price.source) {
            PriceSource.CARDMARKET -> "Cardmarket"
            PriceSource.TCGPLAYER -> "TCGplayer"
        }
        val origin = if (price.currency != currency && converted) " (${price.currency.name})" else ""
        val day = DATE.format(price.updatedAt.atZone(SHOP_ZONE).toLocalDate())
        return if (converted) "$name$origin, $day" else "$name (${price.currency.name}, geen koers), $day"
    }
}
