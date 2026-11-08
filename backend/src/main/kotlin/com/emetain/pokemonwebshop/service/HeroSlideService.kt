package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.HeroSlide
import com.emetain.pokemonwebshop.dto.HeroSlideRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.HeroSlideRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** The slides of the hero carousel after the binder. */
@Service
class HeroSlideService(
    private val slideRepository: HeroSlideRepository,
    private val mediaStorage: MediaStorage,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun showing(): List<HeroSlide> {
        val now = clock.instant()
        return slideRepository.findAllInOrder().filter { it.showsAt(now) }
    }

    @Transactional(readOnly = true)
    fun all(): List<HeroSlide> = slideRepository.findAllInOrder()

    @Transactional
    fun create(request: HeroSlideRequest): HeroSlide {
        val slide = HeroSlide(title = "", position = slideRepository.findHighestPosition() + 1, createdAt = clock.instant())
        apply(slide, request)
        return slideRepository.save(slide)
    }

    @Transactional
    fun update(slideId: Long, request: HeroSlideRequest): HeroSlide = find(slideId).also { apply(it, request) }

    @Transactional
    fun delete(slideId: Long) {
        val slide = find(slideId)
        slide.imageUrl?.let(mediaStorage::delete)
        slideRepository.delete(slide)
    }

    /** Swaps the slide with its neighbour. [up] moves it towards the start of the carousel. */
    @Transactional
    fun move(slideId: Long, up: Boolean): List<HeroSlide> {
        val slides = slideRepository.findAllInOrder().toMutableList()
        val index = slides.indexOfFirst { it.id == slideId }
        if (index < 0) throw ApiException.notFound("We konden deze slide niet vinden.")
        val other = if (up) index - 1 else index + 1
        if (other in slides.indices) {
            slides[index] = slides[other].also { slides[other] = slides[index] }
        }
        slides.forEachIndexed { position, slide -> slide.position = position + 1 }
        return slides
    }

    @Transactional
    fun replaceImage(slideId: Long, bytes: ByteArray): HeroSlide {
        val slide = find(slideId)
        val format = ImageFormat.detect(bytes) ?: throw ApiException.validation("Kies een afbeelding in JPG, PNG of WebP.")
        val previous = slide.imageUrl
        slide.imageUrl = mediaStorage.store(bytes, format)
        previous?.let(mediaStorage::delete)
        return slide
    }

    @Transactional
    fun removeImage(slideId: Long): HeroSlide {
        val slide = find(slideId)
        slide.imageUrl?.let(mediaStorage::delete)
        slide.imageUrl = null
        return slide
    }

    private fun apply(slide: HeroSlide, request: HeroSlideRequest) {
        val link = request.linkUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (link != null && !SAFE_LINK.matches(link)) {
            throw ApiException.validation("Een link begint met / voor een pagina in de shop, of met https://.")
        }
        val label = request.buttonLabel?.trim()?.takeIf { it.isNotEmpty() }
        if ((label == null) != (link == null)) {
            throw ApiException.validation("Vul de knoptekst en de link allebei in, of allebei niet.")
        }
        if (request.startsAt != null && request.endsAt != null && !request.endsAt.isAfter(request.startsAt)) {
            throw ApiException.validation("De einddatum moet na de startdatum liggen.")
        }
        slide.title = request.title.trim()
        slide.text = request.text?.trim()?.takeIf { it.isNotEmpty() }
        slide.buttonLabel = label
        slide.linkUrl = link
        slide.game = request.game?.takeIf { it.isNotBlank() }?.let {
            Game.fromSlug(it) ?: throw ApiException.validation("Kies een geldig spel.")
        }
        slide.active = request.active
        slide.startsAt = request.startsAt
        slide.endsAt = request.endsAt
    }

    private fun find(slideId: Long): HeroSlide =
        slideRepository.findById(slideId).orElseThrow { ApiException.notFound("We konden deze slide niet vinden.") }

    private companion object {
        /** A path in the shop, or an https link. Never `javascript:` or another scheme. */
        val SAFE_LINK = Regex("(/(?!/)[^\\s]*)|(https://[^\\s]+)")
    }
}
