package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.CardVariants
import com.emetain.pokemonwebshop.domain.CollectionValueSnapshot
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.ExchangeRate
import com.emetain.pokemonwebshop.repository.CollectionValueSnapshotRepository
import com.emetain.pokemonwebshop.repository.ExchangeRateRepository
import com.emetain.pokemonwebshop.repository.UserRepository
import com.emetain.pokemonwebshop.service.CollectionSnapshotJob
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

class CollectionHistoryIntegrationTest : CollectionTestSupport() {

    @Autowired
    private lateinit var snapshotRepository: CollectionValueSnapshotRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var exchangeRateRepository: ExchangeRateRepository

    @Autowired
    private lateinit var job: CollectionSnapshotJob

    @Autowired
    private lateinit var clock: Clock

    private val email = uniqueEmail()
    private lateinit var session: Cookie
    private val charizard = uniqueCode()

    private val today get() = clock.instant().atZone(ZoneId.of("Europe/Amsterdam")).toLocalDate()

    @BeforeEach
    fun importCard() {
        exchangeRateRepository.save(ExchangeRate(Currency.USD, BigDecimal("1.25"), LocalDate.of(2026, 11, 5)))
        session = loggedInUser(email = email)
        importCards(listOf(providerCard(charizard, name = "Charizard", prices = listOf(providerPrice(trend = 1000)))))
    }

    private val user get() = userRepository.findByEmail(email)!!

    private fun saved() = snapshotRepository.findAllByUserIdOrderBySnapshotDate(user.id)

    private fun history() = mockMvc.perform(get("/v1/collection/history").cookie(session)).andExpect(status().isOk)

    @Test
    fun `the history needs a login`() {
        mockMvc.perform(get("/v1/collection/history")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a new customer has one point, worth nothing yet`() {
        history()
            .andExpect(jsonPath("$.currency").value("EUR"))
            .andExpect(jsonPath("$.points.length()").value(1))
            .andExpect(jsonPath("$.points[0].date").value(today.toString()))
            .andExpect(jsonPath("$.points[0].cents").value(0))
    }

    @Test
    fun `adding a card moves today's point right away`() {
        add(session, card(charizard).id, quantity = 2)

        history().andExpect(jsonPath("$.points[0].cents").value(2000))
    }

    @Test
    fun `removing a card moves today's point right away, even after today's snapshot`() {
        val entry = add(session, card(charizard).id).entryId(CardVariants.NORMAL)
        job.run()

        mockMvc.perform(delete("/v1/collection/entries/$entry").cookie(session)).andExpect(status().isOk)

        history()
            .andExpect(jsonPath("$.points.length()").value(1))
            .andExpect(jsonPath("$.points[0].cents").value(0))
    }

    @Test
    fun `the job saves one value per customer per day`() {
        add(session, card(charizard).id)

        job.run()
        add(session, card(charizard).id)
        job.run()

        assertEquals(listOf(today to 2000L), saved().map { it.snapshotDate to it.valueCents })
    }

    @Test
    fun `the job skips customers without cards`() {
        job.run()

        assertEquals(emptyList(), saved())
    }

    @Test
    fun `earlier days come first and today is live`() {
        add(session, card(charizard).id)
        snapshotRepository.save(CollectionValueSnapshot(user, today.minusDays(1), 800))
        snapshotRepository.save(CollectionValueSnapshot(user, today.minusDays(30), 500))

        history()
            .andExpect(jsonPath("$.points.length()").value(3))
            .andExpect(jsonPath("$.points[0].date").value(today.minusDays(30).toString()))
            .andExpect(jsonPath("$.points[0].cents").value(500))
            .andExpect(jsonPath("$.points[1].cents").value(800))
            .andExpect(jsonPath("$.points[2].date").value(today.toString()))
            .andExpect(jsonPath("$.points[2].cents").value(1000))
    }

    @Test
    fun `values are saved in euros and shown in the chosen currency`() {
        add(session, card(charizard).id)
        mockMvc.perform(patch("/v1/account").cookie(session).json(mapOf("currency" to "USD"))).andExpect(status().isOk)

        job.run()
        assertEquals(listOf(1000L), saved().map { it.valueCents })

        snapshotRepository.save(CollectionValueSnapshot(user, today.minusDays(1), 2000))
        history()
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.points[0].cents").value(2500))
            .andExpect(jsonPath("$.points[1].cents").value(1250))
    }

    @Test
    fun `a customer only sees their own history`() {
        add(session, card(charizard).id)
        job.run()

        val other = loggedInUser()
        mockMvc.perform(get("/v1/collection/history").cookie(other))
            .andExpect(jsonPath("$.points.length()").value(1))
            .andExpect(jsonPath("$.points[0].cents").value(0))
    }
}
