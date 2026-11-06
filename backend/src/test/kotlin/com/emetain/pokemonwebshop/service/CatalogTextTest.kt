package com.emetain.pokemonwebshop.service

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class CatalogTextTest {

    @ParameterizedTest
    @CsvSource(
        "Pokémon,pokemon",
        "Ho-Oh,ho oh",
        "Mr. Mime,mr mime",
        "Farfetch'd,farfetch d",
        "'  Charizard   ex ',charizard ex",
        "Delver of Secrets // Insectile Aberration,delver of secrets insectile aberration",
        "Nidoran♀,nidoran",
        "Lim-Dûl's Vault,lim dul s vault",
    )
    fun `search names have no case, accents or punctuation`(name: String, expected: String) {
        assertEquals(expected, CatalogText.nameSearch(name))
    }

    @ParameterizedTest
    @CsvSource("TG01,TG000001", "2,000002", "10,000010", "OP01-077,OP000001-000077", "don_132,don_000132", "1234567,1234567", "SV,SV")
    fun `every group of digits is padded`(number: String, expected: String) {
        assertEquals(expected, CatalogText.sortKey(number))
    }

    @ParameterizedTest
    @CsvSource("2,10", "9a,10a", "TG02,TG10")
    fun `numbers sort by value`(lower: String, higher: String) {
        assert(CatalogText.sortKey(lower) < CatalogText.sortKey(higher))
    }
}
