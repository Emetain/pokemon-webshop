package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.HeroSlide
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * A slide as the home page shows it: one from admin, or one the shop adds by itself for an upcoming or live drop.
 * A drop slide has [drop], and the page writes its button and countdown.
 */
data class HeroSlideDto(
    val id: Long,
    /** Unique over both kinds of slides. */
    val key: String,
    val title: String,
    val text: String?,
    val buttonLabel: String?,
    val linkUrl: String?,
    val imageUrl: String?,
    val game: String?,
    val drop: HeroDropDto? = null,
) {
    companion object {
        fun from(slide: HeroSlide) = HeroSlideDto(
            id = slide.id,
            key = "slide-${slide.id}",
            title = slide.title,
            text = slide.text,
            buttonLabel = slide.buttonLabel,
            linkUrl = slide.linkUrl,
            imageUrl = slide.imageUrl,
            game = slide.game?.slug,
        )
    }
}

data class HeroDropDto(
    val slug: String,
    /** `SCHEDULED` or `LIVE`. */
    val status: String,
    val startsAt: Instant,
    val endsAt: Instant?,
    /** For the countdown: the browser's clock can be off. */
    val serverTime: Instant,
)

/** A slide as the admin manages it. [showing] says whether the home page shows it right now. */
data class AdminHeroSlideDto(
    val id: Long,
    val title: String,
    val text: String?,
    val buttonLabel: String?,
    val linkUrl: String?,
    val imageUrl: String?,
    val game: String?,
    val active: Boolean,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val position: Int,
    val showing: Boolean,
) {
    companion object {
        fun from(slide: HeroSlide, now: Instant) = AdminHeroSlideDto(
            id = slide.id,
            title = slide.title,
            text = slide.text,
            buttonLabel = slide.buttonLabel,
            linkUrl = slide.linkUrl,
            imageUrl = slide.imageUrl,
            game = slide.game?.slug,
            active = slide.active,
            startsAt = slide.startsAt,
            endsAt = slide.endsAt,
            position = slide.position,
            showing = slide.showsAt(now),
        )
    }
}

data class HeroSlideRequest(
    @field:NotBlank(message = "Vul een titel in.")
    @field:Size(max = 120, message = "De titel mag maximaal 120 tekens hebben.")
    val title: String = "",
    @field:Size(max = 300, message = "De tekst mag maximaal 300 tekens hebben.")
    val text: String? = null,
    @field:Size(max = 40, message = "De knoptekst mag maximaal 40 tekens hebben.")
    val buttonLabel: String? = null,
    @field:Size(max = 300, message = "De link mag maximaal 300 tekens hebben.")
    val linkUrl: String? = null,
    val game: String? = null,
    val active: Boolean = true,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
)
