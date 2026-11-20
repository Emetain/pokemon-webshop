package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.repository.UserRepository
import com.emetain.pokemonwebshop.service.PriceAlertJob
import jakarta.mail.internet.MimeMessage
import jakarta.servlet.http.Cookie
import org.apache.pdfbox.Loader
import org.awaitility.Awaitility.await
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.Date
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 12, "Done when": a customer with Premium gets the price graph of a card, risers and fallers, price alerts with
 * one mail, the export as CSV and PDF, extra binders and a colour theme; without Premium the API refuses each of these.
 */
class MorePremiumIntegrationTest : CollectionTestSupport() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var alertJob: PriceAlertJob

    @Autowired
    private lateinit var clock: Clock

    private val today get() = clock.instant().atZone(ZoneId.of("Europe/Amsterdam")).toLocalDate()

    private val code = uniqueCode()
    private val charizardId = uniqueCode()
    private val pikachuId = uniqueCode()
    private val name = uniqueWord()
    private var charizard = 0L
    private var pikachu = 0L

    private fun importPrices(charizardCents: Long, pikachuCents: Long) {
        importCards(
            listOf(
                providerCard(charizardId, number = "4", name = "Charizard $name", prices = listOf(providerPrice(trend = charizardCents))),
                providerCard(
                    pikachuId,
                    number = "25",
                    name = "Pikachu $name",
                    variants = setOf(CardVariants.NORMAL, CardVariants.REVERSE),
                    prices = listOf(providerPrice(trend = pikachuCents), providerPrice(CardVariants.REVERSE, trend = 300)),
                ),
            ),
            code = code,
        )
    }

    @BeforeEach
    fun importCatalog() {
        importPrices(charizardCents = 100_00, pikachuCents = 2_00)
        charizard = card(charizardId).id
        pikachu = card(pikachuId).id
    }

    private fun premiumMember(email: String = uniqueEmail()): Cookie {
        val session = loggedInUser(email = email)
        val user = userRepository.findByEmail(email)!!
        user.premiumForever = true
        userRepository.save(user)
        return session
    }

    private fun endPremium(email: String) {
        val user = userRepository.findByEmail(email)!!
        user.premiumForever = false
        userRepository.save(user)
    }

    /** A price as a sync saved it on an earlier day. */
    private fun savedPrice(cardId: Long, date: LocalDate, cents: Long, variant: String = CardVariants.NORMAL) {
        jdbc.update(
            "insert into card_price_history (card_id, variant, source, price_date, currency, trend_cents, average_cents) " +
                "values (?, ?, 'CARDMARKET', ?, 'EUR', ?, null)",
            cardId, variant, Date.valueOf(date), cents,
        )
    }

    private fun mailsTo(email: String): List<MimeMessage> = mailSender.sent.filter { message ->
        message.allRecipients.any { it.toString() == email }
    }

    @Test
    fun `without Premium the API refuses every new Premium feature, and dark mode stays free`() {
        val customer = loggedInUser()

        fun refused(action: org.springframework.test.web.servlet.RequestBuilder) =
            mockMvc.perform(action).andExpect(status().isForbidden).andExpect(jsonPath("$.error").value("premium_required"))

        refused(get("/v1/catalog/cards/$charizard/history").cookie(customer))
        refused(get("/v1/collection/movers?period=1W").cookie(customer))
        refused(post("/v1/price-alerts").cookie(customer).json(mapOf("cardId" to charizard, "kind" to "BELOW", "thresholdCents" to 5000)))
        refused(get("/v1/collection/export.csv").cookie(customer))
        refused(get("/v1/collection/export.pdf").cookie(customer))
        refused(post("/v1/binders").cookie(customer).json(mapOf("name" to "Ruilbinder")))
        refused(patch("/v1/account").cookie(customer).json(mapOf("colorTheme" to "FIRE")))

        mockMvc.perform(patch("/v1/account").cookie(customer).json(mapOf("colorMode" to "DARK")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.colorMode").value("DARK"))
            .andExpect(jsonPath("$.colorTheme").value("POKETOPIA"))
        mockMvc.perform(get("/v1/shop/ads").cookie(customer)).andExpect(status().isOk)
    }

    @Test
    fun `logged out, the Premium endpoints ask for a login`() {
        mockMvc.perform(get("/v1/catalog/cards/$charizard/history")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/v1/price-alerts")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/v1/binders")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a Premium member sees the price graph of a card, one point per day`() {
        val member = premiumMember()
        savedPrice(charizard, today.minusDays(3), 80_00)
        savedPrice(charizard, today.minusDays(1), 90_00)

        mockMvc.perform(get("/v1/catalog/cards/$charizard/history").cookie(member))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currency").value("EUR"))
            .andExpect(jsonPath("$.variants[0].variant").value("normal"))
            .andExpect(jsonPath("$.variants[0].points.length()").value(4))
            .andExpect(jsonPath("$.variants[0].points[0].cents").value(80_00))
            .andExpect(jsonPath("$.variants[0].points[1].cents").value(80_00))
            .andExpect(jsonPath("$.variants[0].points[2].cents").value(90_00))
            .andExpect(jsonPath("$.variants[0].points[3].cents").value(100_00))
    }

    @Test
    fun `risers and fallers among the member's own cards`() {
        val member = premiumMember()
        add(member, charizard)
        add(member, pikachu, CardVariants.NORMAL)
        savedPrice(charizard, today.minusDays(10), 60_00)
        savedPrice(pikachu, today.minusDays(10), 5_00)

        mockMvc.perform(get("/v1/collection/movers?period=1W").cookie(member))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.period").value("1W"))
            .andExpect(jsonPath("$.since").value(today.minusDays(7).toString()))
            .andExpect(jsonPath("$.risers[0].card.id").value(charizard))
            .andExpect(jsonPath("$.risers[0].changeCents").value(40_00))
            .andExpect(jsonPath("$.fallers[0].card.id").value(pikachu))
            .andExpect(jsonPath("$.fallers[0].thenCents").value(5_00))
            .andExpect(jsonPath("$.fallers[0].nowCents").value(2_00))
        mockMvc.perform(get("/v1/collection/movers?period=5Y").cookie(member)).andExpect(status().isBadRequest)
    }

    @Test
    fun `price alerts go off once after the sync, in one mail, with a link that switches them off`() {
        val email = uniqueEmail()
        val member = premiumMember(email)
        mockMvc.perform(post("/v1/price-alerts").cookie(member).json(mapOf("cardId" to charizard, "kind" to "BELOW", "thresholdCents" to 90_00)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.variant").value("normal"))
            .andExpect(jsonPath("$.currency").value("EUR"))
        mockMvc.perform(
            post("/v1/price-alerts").cookie(member)
                .json(mapOf("cardId" to pikachu, "variant" to "normal", "kind" to "CHANGE", "percent" to 50)),
        ).andExpect(status().isOk).andExpect(jsonPath("$.baselineCents").value(2_00))
        mockMvc.perform(post("/v1/price-alerts").cookie(member).json(mapOf("cardId" to pikachu, "variant" to "holo", "kind" to "CHANGE", "percent" to 5)))
            .andExpect(status().isBadRequest)

        alertJob.run()
        assertTrue(mailsTo(email).isEmpty(), "nothing moved yet")

        // The next sync: Charizard drops under €90, Pikachu doubles.
        importPrices(charizardCents = 85_00, pikachuCents = 4_00)
        alertJob.run()
        // Mails go out in the background.
        await().atMost(Duration.ofSeconds(5)).until { mailsTo(email).isNotEmpty() }
        val mails = mailsTo(email)
        assertEquals(1, mails.size)
        assertEquals("2 prijsalerts van vandaag", mails.single().subject)
        val text = plainText(mails.single())!!
        assertTrue("Nu € 85,00: onder je grens van € 90,00." in text, text)
        assertTrue("100% gestegen: van € 2,00 naar € 4,00." in text, text)

        // At most one mail a day, even when the job runs again.
        importPrices(charizardCents = 85_00, pikachuCents = 9_00)
        alertJob.run()
        assertEquals(1, mailsTo(email).size)

        mockMvc.perform(get("/v1/price-alerts").cookie(member))
            .andExpect(jsonPath("$.alerts.length()").value(2))
            .andExpect(jsonPath("$.premium").value(true))

        val charizardPart = text.substringAfter("Charizard $name")
        val token = Regex("prijsalerts/uit\\?token=([A-Za-z0-9_-]+)").find(charizardPart)!!.groupValues[1]
        mockMvc.perform(post("/v1/price-alerts/off").json(mapOf("token" to token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cardName").value("Charizard $name"))
        mockMvc.perform(post("/v1/price-alerts/off").json(mapOf("token" to token))).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/price-alerts").cookie(member)).andExpect(jsonPath("$.alerts.length()").value(1))
    }

    @Test
    fun `alerts wait while Premium has ended, and can still be removed`() {
        val email = uniqueEmail()
        val member = premiumMember(email)
        val id = objectMapper.readTree(
            mockMvc.perform(post("/v1/price-alerts").cookie(member).json(mapOf("cardId" to charizard, "kind" to "BELOW", "thresholdCents" to 90_00)))
                .andReturn().response.contentAsString,
        )["id"].asLong()
        endPremium(email)

        importPrices(charizardCents = 50_00, pikachuCents = 2_00)
        alertJob.run()
        assertTrue(mailsTo(email).isEmpty())

        mockMvc.perform(get("/v1/price-alerts").cookie(member)).andExpect(jsonPath("$.premium").value(false))
        mockMvc.perform(delete("/v1/price-alerts/$id").cookie(member)).andExpect(status().isNoContent)
    }

    @Test
    fun `the export has every owned card with its price, the total and the date`() {
        val member = premiumMember()
        add(member, charizard, quantity = 2)
        add(member, pikachu, CardVariants.REVERSE)

        val csv = mockMvc.perform(get("/v1/collection/export.csv").cookie(member))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"poketopia-collectie-$today.csv\""))
            .andReturn().response.getContentAsString(Charsets.UTF_8)
        assertTrue("Charizard $name;4;Normaal;;2;100,00;" in csv, csv)
        assertTrue("Pikachu $name;25;Reverse holo;;1;3,00;" in csv, csv)
        assertTrue("Totale waarde (EUR);203,00" in csv, csv)

        val pdf = mockMvc.perform(get("/v1/collection/export.pdf").cookie(member))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andReturn().response.contentAsByteArray
        Loader.loadPDF(pdf).use { document ->
            val text = PDFTextStripper().getText(document)
            assertTrue("Charizard $name" in text, text)
            assertTrue("Totale waarde: € 203,00" in text, text)
        }
    }

    @Test
    fun `extra binders have their own value, and stay visible but can't change when Premium ends`() {
        val email = uniqueEmail()
        val member = premiumMember(email)
        val binder = objectMapper.readTree(
            mockMvc.perform(post("/v1/binders").cookie(member).json(mapOf("name" to "  Ruil   binder ")))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("Ruil binder"))
                .andReturn().response.contentAsString,
        )["id"].asLong()

        mockMvc.perform(post("/v1/binders/$binder/entries").cookie(member).json(mapOf("cardId" to charizard, "quantity" to 2)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entries[0].binderName").value("Ruil binder"))
            .andExpect(jsonPath("$.entries[0].quantity").value(2))
        mockMvc.perform(post("/v1/binders/$binder/entries").cookie(member).json(mapOf("cardId" to pikachu, "variant" to "reverse")))
            .andExpect(status().isOk)

        mockMvc.perform(get("/v1/binders").cookie(member))
            .andExpect(jsonPath("$.binders[0].copies").value(3))
            .andExpect(jsonPath("$.binders[0].valueCents").value(203_00))
        // The collection doesn't change: binders don't count for progress or value.
        mockMvc.perform(get("/v1/collection").cookie(member)).andExpect(jsonPath("$.copies").value(0))

        endPremium(email)
        mockMvc.perform(get("/v1/binders/$binder").cookie(member))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.premium").value(false))
            .andExpect(jsonPath("$.entries.length()").value(2))
        mockMvc.perform(post("/v1/binders/$binder/entries").cookie(member).json(mapOf("cardId" to charizard)))
            .andExpect(status().isForbidden)
        val entry = objectMapper.readTree(
            mockMvc.perform(get("/v1/binders/$binder").cookie(member)).andReturn().response.contentAsString,
        )["entries"][0]["id"].asLong()
        mockMvc.perform(put("/v1/binders/$binder/entries/$entry").cookie(member).json(mapOf("quantity" to 0)))
            .andExpect(status().isForbidden)
        mockMvc.perform(delete("/v1/binders/$binder").cookie(member)).andExpect(status().isForbidden)
    }

    @Test
    fun `another customer can't see or change someone's binder`() {
        val owner = premiumMember()
        val binder = objectMapper.readTree(
            mockMvc.perform(post("/v1/binders").cookie(owner).json(mapOf("name" to "Van mij"))).andReturn().response.contentAsString,
        )["id"].asLong()
        val other = premiumMember()

        mockMvc.perform(get("/v1/binders/$binder").cookie(other)).andExpect(status().isNotFound)
        mockMvc.perform(post("/v1/binders/$binder/entries").cookie(other).json(mapOf("cardId" to charizard))).andExpect(status().isNotFound)
    }

    @Test
    fun `a Premium member picks a colour theme, which falls back to Poketopia's own when Premium ends`() {
        val email = uniqueEmail()
        val member = premiumMember(email)

        mockMvc.perform(patch("/v1/account").cookie(member).json(mapOf("colorTheme" to "FIRE", "colorMode" to "SYSTEM")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.colorTheme").value("FIRE"))
            .andExpect(jsonPath("$.colorMode").value("SYSTEM"))

        endPremium(email)
        mockMvc.perform(get("/v1/auth/me").cookie(member))
            .andExpect(jsonPath("$.colorTheme").value("POKETOPIA"))
            .andExpect(jsonPath("$.colorMode").value("SYSTEM"))
    }
}
