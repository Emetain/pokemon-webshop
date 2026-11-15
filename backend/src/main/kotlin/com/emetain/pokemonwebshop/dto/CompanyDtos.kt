package com.emetain.pokemonwebshop.dto

/** The business details in the footer. A value that isn't filled in is null. */
data class CompanyDto(
    val legalName: String?,
    val address: String?,
    val kvkNumber: String?,
    val vatNumber: String?,
)
