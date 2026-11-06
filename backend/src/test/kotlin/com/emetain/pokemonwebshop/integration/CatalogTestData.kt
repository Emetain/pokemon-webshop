package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.service.provider.ProviderCard
import com.emetain.pokemonwebshop.service.provider.ProviderPrice
import com.emetain.pokemonwebshop.service.provider.ProviderSet
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

val PRICE_DATE: Instant = Instant.parse("2026-11-05T10:00:00Z")

fun uniqueCode() = UUID.randomUUID().toString().take(8)

/** A name no other test uses, so a search only finds the cards of its own test. */
fun uniqueWord() = uniqueCode().map { 'a' + it.digitToInt(16) }.joinToString("")

fun providerSet(
    code: String = uniqueCode(),
    cards: List<ProviderCard> = emptyList(),
    name: String = "Set $code",
    series: String = "Scarlet & Violet",
    releaseDate: LocalDate? = LocalDate.of(2024, 1, 1),
    failedCards: Int = 0,
) = ProviderSet(
    externalId = "ext-$code",
    code = code,
    name = name,
    series = series,
    releaseDate = releaseDate,
    cardCount = cards.size,
    logoUrl = "https://example.com/$code.webp",
    symbolUrl = null,
    sortOrder = 0,
    cards = cards,
    failedCards = failedCards,
)

fun providerCard(
    externalId: String = uniqueCode(),
    number: String = "1",
    name: String = "Pikachu",
    rarity: String? = "Rare",
    variants: Set<String> = setOf(CardVariants.NORMAL),
    dexNumbers: Set<Int> = emptySet(),
    prices: List<ProviderPrice> = emptyList(),
    category: String? = "Pokemon",
    colors: Set<String> = emptySet(),
) = ProviderCard(
    externalId = externalId,
    number = number,
    name = name,
    rarity = rarity,
    cardType = "Pokemon",
    category = category,
    imageSmallUrl = "https://example.com/$externalId/low.webp",
    imageLargeUrl = "https://example.com/$externalId/high.webp",
    variants = variants,
    colors = colors,
    dexNumbers = dexNumbers,
    prices = prices,
)

fun providerPrice(variant: String = CardVariants.NORMAL, trend: Long? = 100, average: Long? = 110) =
    ProviderPrice(variant, trend, average, PRICE_DATE)
