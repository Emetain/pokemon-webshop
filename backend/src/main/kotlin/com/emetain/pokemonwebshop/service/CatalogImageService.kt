package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Card
import com.emetain.pokemonwebshop.domain.CardSet
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.CardSetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Lets the shop replace the picture of a card or set. The images of the source stay stored underneath. */
@Service
class CatalogImageService(
    private val cardRepository: CardRepository,
    private val cardSetRepository: CardSetRepository,
    private val mediaStorage: MediaStorage,
) {

    @Transactional
    fun replaceCardImage(cardId: Long, bytes: ByteArray): Card {
        val card = findCard(cardId)
        val previous = card.customImageUrl
        card.customImageUrl = store(bytes)
        previous?.let(mediaStorage::delete)
        return card
    }

    @Transactional
    fun resetCardImage(cardId: Long): Card {
        val card = findCard(cardId)
        card.customImageUrl?.let(mediaStorage::delete)
        card.customImageUrl = null
        return card
    }

    @Transactional
    fun replaceSetLogo(game: Game, code: String, bytes: ByteArray): CardSet {
        val set = findSet(game, code)
        val previous = set.customLogoUrl
        set.customLogoUrl = store(bytes)
        previous?.let(mediaStorage::delete)
        return set
    }

    @Transactional
    fun resetSetLogo(game: Game, code: String): CardSet {
        val set = findSet(game, code)
        set.customLogoUrl?.let(mediaStorage::delete)
        set.customLogoUrl = null
        return set
    }

    private fun store(bytes: ByteArray): String {
        val format = ImageFormat.detect(bytes)
            ?: throw ApiException.validation("Kies een afbeelding in JPG, PNG of WebP.")
        return mediaStorage.store(bytes, format)
    }

    private fun findCard(cardId: Long): Card =
        cardRepository.findById(cardId).orElseThrow { ApiException.notFound("We konden deze kaart niet vinden.") }

    private fun findSet(game: Game, code: String): CardSet =
        cardSetRepository.findByGameAndCode(game, code) ?: throw ApiException.notFound("We konden deze set niet vinden.")
}
