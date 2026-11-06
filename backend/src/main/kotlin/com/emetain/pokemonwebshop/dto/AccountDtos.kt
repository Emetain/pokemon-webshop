package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.Address
import com.emetain.pokemonwebshop.domain.Currency
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** Fields left out stay as they are. */
data class UpdateProfileRequest(
    @field:Pattern(regexp = ".*\\S.*", message = "Vul je naam in.")
    @field:Size(max = 100, message = "Je naam mag maximaal 100 tekens lang zijn.")
    val displayName: String? = null,

    val country: String? = null,

    val currency: Currency? = null,
)

data class AddressRequest(
    @field:NotBlank(message = "Vul de straat in.")
    @field:Size(max = 100, message = "De straat mag maximaal 100 tekens lang zijn.")
    val street: String = "",

    @field:NotBlank(message = "Vul het huisnummer in.")
    @field:Size(max = 20, message = "Het huisnummer mag maximaal 20 tekens lang zijn.")
    val houseNumber: String = "",

    @field:NotBlank(message = "Vul de postcode in.")
    @field:Size(max = 10, message = "De postcode mag maximaal 10 tekens lang zijn.")
    val postcode: String = "",

    @field:NotBlank(message = "Vul de plaats in.")
    @field:Size(max = 100, message = "De plaats mag maximaal 100 tekens lang zijn.")
    val city: String = "",

    @field:NotBlank(message = "Kies een land.")
    val country: String = "",
)

data class AddressDto(
    val id: Long,
    val street: String,
    val houseNumber: String,
    val postcode: String,
    val city: String,
    val country: String,
) {
    companion object {
        fun from(address: Address) = AddressDto(
            id = address.id,
            street = address.street,
            houseNumber = address.houseNumber,
            postcode = address.postcode,
            city = address.city,
            country = address.country,
        )
    }
}
