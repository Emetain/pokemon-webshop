package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.config.AdsMode
import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.dto.AdsDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * The AdSense settings from the server (`app.ads`), for the frontend and for `/ads.txt`. Ads are on only with
 * `ADS=adsense`, a publisher id that looks like one, and at least one ad unit.
 */
@Component
class AdsOrchestrator(
    private val properties: AppProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val settings: AdsDto by lazy { read() }

    fun ads(): AdsDto = settings

    /**
     * The line AdSense asks for in `/ads.txt`: it says this account may sell ads on this site. Null without ads.
     * `f08c47fec0942fa0` is Google's own id, the same for every publisher.
     */
    fun adsTxt(): String? =
        settings.client?.let { "google.com, ${it.removePrefix("ca-")}, DIRECT, f08c47fec0942fa0\n" }

    private fun read(): AdsDto {
        val ads = properties.ads
        if (ads.mode != AdsMode.ADSENSE) return AdsDto(enabled = false)
        val client = ads.adsenseClient.trim()
        if (!CLIENT.matches(client)) {
            log.error("ADS=adsense, but ADSENSE_CLIENT isn't a publisher id (ca-pub- and 16 digits). Ads stay off.")
            return AdsDto(enabled = false)
        }
        val slots = mapOf("catalog" to ads.slotCatalog, "content" to ads.slotContent)
            .mapValues { it.value.trim() }
            .filterValues { it.isNotEmpty() }
        val wrong = slots.filterValues { !SLOT.matches(it) }.keys
        if (wrong.isNotEmpty()) log.error("These ad units aren't numbers and are left out: {}", wrong)
        val usable = slots - wrong
        if (usable.isEmpty()) {
            log.warn("ADS=adsense without an ad unit (ADSENSE_SLOT_CATALOG, ADSENSE_SLOT_CONTENT): only /ads.txt is served.")
        }
        return AdsDto(enabled = usable.isNotEmpty(), client = client, slots = usable)
    }

    private companion object {
        val CLIENT = Regex("ca-pub-\\d{16}")
        val SLOT = Regex("\\d{6,20}")
    }
}
