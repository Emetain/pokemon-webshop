package com.emetain.pokemonwebshop.service

import java.math.BigDecimal
import java.math.RoundingMode

class CsvFormatException(message: String) : RuntimeException(message)

/**
 * Reads and writes CSV the way spreadsheets do. Dutch Excel uses semicolons and a decimal comma, so that is what
 * this writes. Reading accepts commas and semicolons, and a decimal comma or point.
 */
object ProductCsv {

    const val DELIMITER = ';'

    /** Lets Excel read the file as UTF-8, so "Pokémon" doesn't turn into "PokÃ©mon". */
    private const val BYTE_ORDER_MARK = '﻿'

    private val FORMULA_START = setOf('=', '+', '-', '@', '\t', '\r')

    fun write(rows: List<List<String>>): String = buildString {
        append(BYTE_ORDER_MARK)
        for (row in rows) {
            append(row.joinToString(DELIMITER.toString(), transform = ::quote))
            append("\r\n")
        }
    }

    /** The rows of [text], the header first. Empty lines are left out. */
    fun parse(text: String): List<List<String>> {
        val content = text.removePrefix(BYTE_ORDER_MARK.toString())
        val delimiter = detectDelimiter(content)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0

        fun endField() {
            row += field.toString()
            field.clear()
        }

        fun endRow() {
            endField()
            if (row.any { it.isNotBlank() }) rows += row
            row = mutableListOf()
        }

        while (index < content.length) {
            val char = content[index]
            when {
                quoted && char == '"' && content.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
                quoted -> field.append(char)
                char == delimiter -> endField()
                char == '\r' && content.getOrNull(index + 1) == '\n' -> {
                    endRow()
                    index++
                }
                char == '\n' || char == '\r' -> endRow()
                else -> field.append(char)
            }
            index++
        }
        if (quoted) throw CsvFormatException("Een aanhalingsteken wordt niet afgesloten.")
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /**
     * An amount in euros as cents: "12,95", "12.95", "€ 1.234,50" and "1234" all work.
     * With both a point and a comma, the last one is the decimal separator.
     */
    fun parseCents(text: String): Long? {
        val compact = text.replace("€", "").replace(" ", "").replace(" ", "").trim()
        if (compact.isEmpty()) return null
        val lastPoint = compact.lastIndexOf('.')
        val lastComma = compact.lastIndexOf(',')
        val decimalAt = when {
            lastPoint >= 0 && lastComma >= 0 -> maxOf(lastPoint, lastComma)
            lastPoint >= 0 -> lastPoint.takeIf { compact.length - it - 1 in 1..2 && compact.count { c -> c == '.' } == 1 }
            lastComma >= 0 -> lastComma.takeIf { compact.length - it - 1 in 1..2 && compact.count { c -> c == ',' } == 1 }
            else -> null
        }
        val normalised = compact.filterIndexed { i, c -> c.isDigit() || i == decimalAt || (i == 0 && c == '-') }
            .let { if (decimalAt != null) it.replace(',', '.') else it }
        val amount = normalised.toBigDecimalOrNull() ?: throw CsvFormatException("\"$text\" is geen bedrag.")
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }

    fun formatCents(cents: Long?): String =
        cents?.let { BigDecimal.valueOf(it, 2).toPlainString().replace('.', ',') }.orEmpty()

    /** Text that starts like a formula gets a quote before it, so a spreadsheet shows it instead of running it. */
    fun safeText(value: String?): String {
        val text = value.orEmpty()
        return if (text.isNotEmpty() && text.first() in FORMULA_START) "'$text" else text
    }

    /** Undoes [safeText]. */
    fun readText(value: String): String =
        if (value.length > 1 && value.first() == '\'' && value[1] in FORMULA_START) value.substring(1) else value

    private fun quote(value: String): String =
        if (value.any { it == DELIMITER || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** Whichever of `;` and `,` the header line has more of, outside quotes. */
    private fun detectDelimiter(content: String): Char {
        var quoted = false
        var semicolons = 0
        var commas = 0
        for (char in content) {
            when {
                char == '"' -> quoted = !quoted
                quoted -> Unit
                char == '\n' || char == '\r' -> break
                char == ';' -> semicolons++
                char == ',' -> commas++
            }
        }
        return if (commas > semicolons) ',' else ';'
    }
}
