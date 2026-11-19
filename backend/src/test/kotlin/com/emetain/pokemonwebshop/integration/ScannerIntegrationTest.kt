package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.repository.UserRepository
import com.emetain.pokemonwebshop.service.CatalogImportService
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Phase 11: the card scanner finds the card in the catalog, and 100 free scans are counted per account. */
class ScannerIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var ai: FakeAiClient

    @Autowired
    private lateinit var importService: CatalogImportService

    @Autowired
    private lateinit var userRepository: UserRepository

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)

    @AfterEach
    fun reset() {
        ai.failScan = false
    }

    private fun scan(session: Cookie?, photo: ByteArray = jpeg): ResultActions {
        val request = multipart("/v1/scanner").file(MockMultipartFile("photo", "kaart.jpg", "image/jpeg", photo)).with(fromRandomIp())
        return mockMvc.perform(if (session != null) request.cookie(session) else request)
    }

    /** A card with a name no other test uses, and what the model reads from its photo. */
    private fun card(): String {
        val name = uniqueWord().replaceFirstChar { it.uppercase() }
        importService.importSet(Game.POKEMON, providerSet(cards = listOf(providerCard(number = "025", name = name))))
        ai.scanReading = """{"game": "pokemon", "name": "$name", "setCode": "XYZ", "setName": null, "number": "25"}"""
        return name
    }

    private fun userId(email: String) = requireNotNull(userRepository.findByEmail(email)).id

    @Test
    fun `a scan finds the card and counts one of the free scans`() {
        val name = card()
        val customer = loggedInUser()

        mockMvc.perform(get("/v1/scanner").cookie(customer)).andExpect(jsonPath("$.scansLeft").value(100))
        scan(customer)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.match.name").value(name))
            .andExpect(jsonPath("$.match.number").value("025"))
            .andExpect(jsonPath("$.reading.name").value(name))
            .andExpect(jsonPath("$.scansLeft").value(99))
    }

    @Test
    fun `a scan without a result or one that fails at the model doesn't count`() {
        val customer = loggedInUser()
        ai.scanReading = """{"game": "unknown", "name": null, "setCode": null, "setName": null, "number": null}"""
        scan(customer).andExpect(status().isOk).andExpect(jsonPath("$.candidates.length()").value(0)).andExpect(jsonPath("$.scansLeft").value(100))

        ai.failScan = true
        scan(customer).andExpect(status().isBadGateway)
        mockMvc.perform(get("/v1/scanner").cookie(customer)).andExpect(jsonPath("$.scansLeft").value(100))
    }

    @Test
    fun `the 101st scan without Premium asks for Premium`() {
        card()
        val email = uniqueEmail()
        val customer = loggedInUser(email = email)
        userRepository.save(userRepository.findById(userId(email)).get().apply { freeScansUsed = 99 })

        scan(customer).andExpect(status().isOk).andExpect(jsonPath("$.scansLeft").value(0))
        val photos = ai.scannedPhotos.size
        scan(customer).andExpect(status().isForbidden).andExpect(jsonPath("$.error").value("premium_required"))
        // Refused before the photo went anywhere.
        kotlin.test.assertEquals(photos, ai.scannedPhotos.size)
    }

    @Test
    fun `an admin scans without a limit`() {
        card()
        scan(loggedInUser(Role.ADMIN)).andExpect(status().isOk).andExpect(jsonPath("$.scansLeft").doesNotExist())
    }

    @Test
    fun `only logged-in customers scan, and only photos`() {
        scan(null).andExpect(status().isUnauthorized)
        scan(loggedInUser(), "not a photo".toByteArray()).andExpect(status().isBadRequest)
    }
}
