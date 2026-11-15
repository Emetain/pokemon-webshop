package com.emetain.pokemonwebshop.dto

import java.time.Instant

/** What a link preview of a page shows, in WhatsApp, Discord and the like. Never a stock count. */
data class SharePage(
    /** The page's own path, such as `/product/destined-rivals-booster-box`. */
    val path: String,
    val title: String,
    val description: String,
    /** Absolute, or a path on the shop such as an uploaded image. Null: no picture. */
    val imageUrl: String?,
    /** `og:type`: `product`, `article` or `website`. */
    val type: String,
)

/** One address in the sitemap, as a path with its segments already encoded. */
data class SitemapEntry(
    val path: String,
    val lastModified: Instant? = null,
)
