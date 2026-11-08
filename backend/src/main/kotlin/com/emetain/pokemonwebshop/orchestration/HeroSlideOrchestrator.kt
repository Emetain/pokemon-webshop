package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.HeroSlide
import com.emetain.pokemonwebshop.dto.AdminHeroSlideDto
import com.emetain.pokemonwebshop.dto.HeroSlideDto
import com.emetain.pokemonwebshop.dto.HeroSlideRequest
import com.emetain.pokemonwebshop.service.HeroSlideService
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class HeroSlideOrchestrator(
    private val slideService: HeroSlideService,
    private val clock: Clock,
) {

    fun showing(): List<HeroSlideDto> = slideService.showing().map(HeroSlideDto::from)

    fun all(): List<AdminHeroSlideDto> = slideService.all().map(::admin)

    fun create(request: HeroSlideRequest): AdminHeroSlideDto = admin(slideService.create(request))

    fun update(slideId: Long, request: HeroSlideRequest): AdminHeroSlideDto = admin(slideService.update(slideId, request))

    fun delete(slideId: Long) = slideService.delete(slideId)

    fun move(slideId: Long, up: Boolean): List<AdminHeroSlideDto> = slideService.move(slideId, up).map(::admin)

    fun replaceImage(slideId: Long, bytes: ByteArray): AdminHeroSlideDto = admin(slideService.replaceImage(slideId, bytes))

    fun removeImage(slideId: Long): AdminHeroSlideDto = admin(slideService.removeImage(slideId))

    private fun admin(slide: HeroSlide) = AdminHeroSlideDto.from(slide, clock.instant())
}
