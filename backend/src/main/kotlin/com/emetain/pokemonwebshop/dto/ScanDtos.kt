package com.emetain.pokemonwebshop.dto

/** What the scanner read, so the customer sees why a card was found or not. */
data class ScanReadingDto(
    val game: String?,
    val name: String?,
    val setCode: String?,
    val setName: String?,
    val number: String?,
)

data class ScanResultDto(
    val reading: ScanReadingDto,
    /** One card the scanner is sure of; otherwise null and the customer picks from [candidates]. */
    val match: CardSummaryDto?,
    val candidates: List<CardSummaryDto>,
    /** Free scans left; null with Premium. */
    val scansLeft: Int?,
)

data class ScannerStatusDto(
    val available: Boolean,
    val premium: Boolean,
    val scansLeft: Int?,
    val freeScans: Int,
)
