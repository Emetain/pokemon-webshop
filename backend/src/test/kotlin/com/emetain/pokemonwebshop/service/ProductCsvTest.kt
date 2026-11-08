package com.emetain.pokemonwebshop.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductCsvTest {

    @Test
    fun `what it writes it reads back the same`() {
        val rows = listOf(
            listOf("id", "name", "description"),
            listOf("1", "Booster; \"special\"", "Line one\nLine two"),
            listOf("2", "Pokémon, Tin", ""),
        )
        assertEquals(rows, ProductCsv.parse(ProductCsv.write(rows)))
    }

    @Test
    fun `it writes semicolons with a byte order mark for Excel`() {
        val text = ProductCsv.write(listOf(listOf("a", "b")))
        assertTrue(text.startsWith("﻿a;b\r\n"))
    }

    @Test
    fun `it reads comma separated files too`() {
        assertEquals(listOf(listOf("id", "name"), listOf("1", "Tin, mini")), ProductCsv.parse("id,name\n1,\"Tin, mini\"\n"))
    }

    @Test
    fun `empty lines are left out and a missing last field counts as empty`() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "")), ProductCsv.parse("a;b\r\n\r\n1;\r\n\r\n"))
    }

    @Test
    fun `an unclosed quote is an error`() {
        assertThrows<CsvFormatException> { ProductCsv.parse("a;b\n\"1;2\n") }
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "12,95|1295", "12.95|1295", "€ 1.234,50|123450", "1,234.50|123450", "1234|123400",
            "1.234|123400", "12,5|1250", "0,99|99", " 7 |700",
        ],
    )
    fun `amounts are read in Dutch and English notation`(text: String, cents: Long) {
        assertEquals(cents, ProductCsv.parseCents(text))
    }

    @Test
    fun `an empty amount is no amount, and text is an error`() {
        assertNull(ProductCsv.parseCents(""))
        assertThrows<CsvFormatException> { ProductCsv.parseCents("gratis") }
    }

    @Test
    fun `amounts are written with a decimal comma`() {
        assertEquals("12,95", ProductCsv.formatCents(1295))
        assertEquals("0,05", ProductCsv.formatCents(5))
        assertEquals("", ProductCsv.formatCents(null))
    }

    @Test
    fun `text that looks like a formula is kept as text`() {
        assertEquals("'=HYPERLINK(1)", ProductCsv.safeText("=HYPERLINK(1)"))
        assertEquals("Booster", ProductCsv.safeText("Booster"))
        assertEquals("=HYPERLINK(1)", ProductCsv.readText("'=HYPERLINK(1)"))
        assertEquals("'t Kofschip", ProductCsv.readText("'t Kofschip"))
    }
}
