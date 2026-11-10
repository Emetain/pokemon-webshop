package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Drop
import com.emetain.pokemonwebshop.domain.DropStatus
import com.emetain.pokemonwebshop.domain.HeroSlide
import com.emetain.pokemonwebshop.dto.AdminHeroSlideDto
import com.emetain.pokemonwebshop.dto.HeroDropDto
import com.emetain.pokemonwebshop.dto.HeroSlideDto
import com.emetain.pokemonwebshop.dto.HeroSlideRequest
import com.emetain.pokemonwebshop.repository.DropRepository
import com.emetain.pokemonwebshop.service.HeroSlideService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class HeroSlideOrchestrator(
    private val slideService: HeroSlideService,
    private val dropRepository: DropRepository,
    private val clock: Clock,
) {

    /** Live drops first, then upcoming drops by start time, then the slides from admin in their order. */
    @Transactional(readOnly = true)
    fun showing(): List<HeroSlideDto> = dropSlides() + slideService.showing().map(HeroSlideDto::from)

    /** A drop gets a slide by itself while it is upcoming or live, unless the admin switched that off. */
    private fun dropSlides(): List<HeroSlideDto> {
        val now = clock.instant()
        val drops = dropRepository.findAllWithProducts(listOf(DropStatus.LIVE, DropStatus.SCHEDULED))
            .filter { it.showInHero }
            .sortedWith(compareBy<Drop> { it.status != DropStatus.LIVE }.thenBy { it.startsAt })
        return drops.map { drop ->
            HeroSlideDto(
                id = drop.id,
                key = "drop-${drop.id}",
                title = drop.name,
                text = drop.description?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.let(::shorten),
                buttonLabel = null,
                linkUrl = "/drops/${drop.slug}",
                imageUrl = drop.bannerUrl,
                game = DropOrchestrator.gameOf(drop),
                drop = HeroDropDto(drop.slug, drop.status.name, drop.startsAt, drop.endsAt, now),
            )
        }
    }

    private fun shorten(text: String): String =
        if (text.length <= SLIDE_TEXT) text else text.take(SLIDE_TEXT - 1).substringBeforeLast(' ').trimEnd(',', '.', ' ') + "…"

    private companion object {
        const val SLIDE_TEXT = 160
    }

    fun all(): List<AdminHeroSlideDto> = slideService.all().map(::admin)

    fun create(request: HeroSlideRequest): AdminHeroSlideDto = admin(slideService.create(request))

    fun update(slideId: Long, request: HeroSlideRequest): AdminHeroSlideDto = admin(slideService.update(slideId, request))

    fun delete(slideId: Long) = slideService.delete(slideId)

    fun move(slideId: Long, up: Boolean): List<AdminHeroSlideDto> = slideService.move(slideId, up).map(::admin)

    fun replaceImage(slideId: Long, bytes: ByteArray): AdminHeroSlideDto = admin(slideService.replaceImage(slideId, bytes))

    fun removeImage(slideId: Long): AdminHeroSlideDto = admin(slideService.removeImage(slideId))

    private fun admin(slide: HeroSlide) = AdminHeroSlideDto.from(slide, clock.instant())
}
