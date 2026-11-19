package com.emetain.pokemonwebshop.dto

/** A rectangle on the photo, in thousandths of its width and height. */
data class BoxDto(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** What the check saw at one corner, edge or the surface: `clean`, `light` or `clear`, with a note when not clean. */
data class WearDto(val position: String, val condition: String, val note: String?)

data class PregradeSideDto(
    val side: String,
    /** False when the photo is too blurry, skewed or cut off to say much. [problem] then says why. */
    val usable: Boolean,
    val problem: String?,
    /** The card's outer edge and the inner frame its centering is measured against. Null when not found. */
    val outer: BoxDto?,
    val inner: BoxDto?,
    val corners: List<WearDto>,
    val edges: List<WearDto>,
    val surface: WearDto?,
)

/** An indication only, never a grade: the page says so above and below it. */
data class PregradeResultDto(val sides: List<PregradeSideDto>)
