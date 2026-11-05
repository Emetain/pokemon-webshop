package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.AddressRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class AddressServiceTest {

    private val repository = mock<AddressRepository>()
    private val service = AddressService(repository, mock(), MutableClock())

    @ParameterizedTest
    @CsvSource("1234AB,1234 AB", "1234 ab,1234 AB", "' 1234  ab ',1234 AB")
    fun `normalizes dutch postcodes`(input: String, expected: String) {
        assertEquals(expected, service.normalizePostcode(input, "NL"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["0123 AB", "123 AB", "1234 A", "12345", "ABCD EF"])
    fun `refuses invalid dutch postcodes`(postcode: String) {
        assertThrows<ApiException> { service.normalizePostcode(postcode, "NL") }
    }

    @Test
    fun `accepts belgian postcodes`() {
        assertEquals("1000", service.normalizePostcode(" 1000 ", "BE"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["0999", "100", "10000", "1234 AB"])
    fun `refuses invalid belgian postcodes`(postcode: String) {
        assertThrows<ApiException> { service.normalizePostcode(postcode, "BE") }
    }

    @Test
    fun `an address of another user counts as not found`() {
        whenever(repository.findByIdAndUserId(5, 1)) doReturn null

        val exception = assertThrows<ApiException> { service.delete(userId = 1, addressId = 5) }

        assertEquals("not_found", exception.code)
    }
}
