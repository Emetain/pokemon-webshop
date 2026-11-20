package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.config.AdsMode
import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.dto.AdsDto
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.service.PremiumService
import com.emetain.pokemonwebshop.service.testProperties
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdsOrchestratorTest {

    private val premiumService = mock<PremiumService>()

    private fun orchestrator(ads: AppProperties.Ads) = AdsOrchestrator(testProperties().copy(ads = ads), premiumService)

    private val client = "ca-pub-1234567890123456"

    @Test
    fun `ads are off by default, and there is no ads txt`() {
        val orchestrator = AdsOrchestrator(testProperties(), premiumService)

        assertEquals(AdsDto(enabled = false), orchestrator.ads())
        assertNull(orchestrator.adsTxt())
    }

    @Test
    fun `a publisher id alone doesn't switch ads on`() {
        val orchestrator = orchestrator(AppProperties.Ads(AdsMode.OFF, client, "1234567890", "2345678901"))

        assertEquals(AdsDto(enabled = false), orchestrator.ads())
        assertNull(orchestrator.adsTxt())
    }

    @Test
    fun `with adsense the frontend gets the publisher id and the ad units that are filled in`() {
        val orchestrator = orchestrator(AppProperties.Ads(AdsMode.ADSENSE, " $client ", " 1234567890", ""))

        assertEquals(AdsDto(enabled = true, client = client, slots = mapOf("catalog" to "1234567890")), orchestrator.ads())
        assertEquals("google.com, pub-1234567890123456, DIRECT, f08c47fec0942fa0\n", orchestrator.adsTxt())
    }

    @Test
    fun `a publisher id that isn't one keeps ads off`() {
        val orchestrator = orchestrator(AppProperties.Ads(AdsMode.ADSENSE, "pub-123\"><script>", "1234567890", ""))

        assertEquals(AdsDto(enabled = false), orchestrator.ads())
        assertNull(orchestrator.adsTxt())
    }

    @Test
    fun `an ad unit that isn't a number is left out`() {
        val orchestrator = orchestrator(AppProperties.Ads(AdsMode.ADSENSE, client, "abc", "2345678901"))

        assertEquals(mapOf("content" to "2345678901"), orchestrator.ads().slots)
    }

    @Test
    fun `without an ad unit only ads txt is served`() {
        val orchestrator = orchestrator(AppProperties.Ads(AdsMode.ADSENSE, client, "", ""))

        assertEquals(AdsDto(enabled = false, client = client), orchestrator.ads())
        assertEquals("google.com, pub-1234567890123456, DIRECT, f08c47fec0942fa0\n", orchestrator.adsTxt())
    }

    @Test
    fun `a Premium member never gets ads, so the app doesn't load Google's script for them`() {
        val orchestrator = orchestrator(AppProperties.Ads(AdsMode.ADSENSE, client, "1234567890", ""))
        val member = mock<User>()
        whenever(premiumService.has(any())).thenAnswer { it.arguments[0] === member }

        assertEquals(AdsDto(enabled = false), orchestrator.ads(member))
        assertEquals(true, orchestrator.ads(null).enabled)
        // ads.txt stays: it's about the site, not about who looks.
        assertEquals("google.com, pub-1234567890123456, DIRECT, f08c47fec0942fa0\n", orchestrator.adsTxt())
    }
}
