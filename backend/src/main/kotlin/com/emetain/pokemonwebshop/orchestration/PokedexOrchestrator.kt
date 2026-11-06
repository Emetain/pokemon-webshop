package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.PokemonSpecies
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.CardListRequest
import com.emetain.pokemonwebshop.dto.CardPageDto
import com.emetain.pokemonwebshop.dto.PokedexDto
import com.emetain.pokemonwebshop.dto.PokedexEntryDto
import com.emetain.pokemonwebshop.dto.PokemonLinkDto
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardSort
import com.emetain.pokemonwebshop.repository.CardSpecifications
import com.emetain.pokemonwebshop.repository.PokemonSpeciesRepository
import com.emetain.pokemonwebshop.service.PokedexService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PokedexOrchestrator(
    private val pokedexService: PokedexService,
    private val speciesRepository: PokemonSpeciesRepository,
    private val cardRepository: CardRepository,
    private val cardList: CardListOrchestrator,
) {

    fun pokedex(viewer: User?): PokedexDto = PokedexDto.from(pokedexService.pokedex(viewer?.id))

    fun entry(dexNumber: Int, viewer: User?): PokedexEntryDto {
        val species = findSpecies(dexNumber)
        return PokedexEntryDto(
            dexNumber = species.dexNumber,
            name = species.name,
            generation = species.generation,
            totalCards = cardRepository.countByDexNumber(dexNumber),
            ownedCards = viewer?.let { cardRepository.countOwnedByDexNumber(it.id, dexNumber) },
            previous = neighbour(dexNumber - 1),
            next = neighbour(dexNumber + 1),
            rarities = cardRepository.findRaritiesForDexNumber(dexNumber),
            variants = CardVariants.displaySorted(cardRepository.findVariantsForDexNumber(dexNumber)),
            colors = cardRepository.findColorsForDexNumber(dexNumber),
        )
    }

    /** Numbers mean nothing across sets, so these start with the most valuable card. */
    @Transactional(readOnly = true)
    fun cards(dexNumber: Int, request: CardListRequest, viewer: User?): CardPageDto {
        findSpecies(dexNumber)
        val sort = CardSort.fromParam(request.sort.filterValue()) ?: CardSort.PRICE_HIGHEST
        val filters = cardList.filters(request, viewer) +
            CardSpecifications.withDexNumber(dexNumber) +
            CardSpecifications.sortedBy(sort)
        return cardList.page(filters, request, viewer)
    }

    private fun neighbour(dexNumber: Int): PokemonLinkDto? =
        speciesRepository.findByIdOrNull(dexNumber)?.let(PokemonLinkDto::from)

    private fun findSpecies(dexNumber: Int): PokemonSpecies =
        speciesRepository.findByIdOrNull(dexNumber) ?: throw ApiException.notFound()
}
