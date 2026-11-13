package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.repository.AiUsageRepository
import com.fasterxml.jackson.databind.JsonNode
import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import jakarta.servlet.http.Cookie
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Customer service: the chatbot, tickets from guests and accounts, and answering them in admin. */
class SupportIntegrationTest : ShopTestSupport() {

    @Autowired
    private lateinit var fakeAi: FakeAiClient

    @Autowired
    private lateinit var aiUsageRepository: AiUsageRepository

    @BeforeEach
    fun reset() {
        fakeAi.configuredInTest = true
    }

    private fun createTicket(body: Map<String, Any?>, session: Cookie? = null): ResultActions {
        val request = post("/v1/support/tickets").with(fromRandomIp()).json(body)
        return mockMvc.perform(if (session != null) request.cookie(session) else request)
    }

    private fun guestTicket(email: String, subject: String = "Levering $word", extra: Map<String, Any?> = emptyMap()): JsonNode =
        createTicket(
            mapOf("name" to "Brock", "email" to email, "orderNumber" to "pt-abc123", "subject" to subject, "message" to "Wanneer komt mijn pakket?") + extra,
        ).andExpect(status().isCreated).json()

    private fun mailsTo(email: String): List<MimeMessage> =
        mailSender.sent.filter { it.getRecipients(Message.RecipientType.TO).first().toString() == email }

    private fun waitForMail(email: String, subject: String): MimeMessage =
        await().atMost(Duration.ofSeconds(5)).until({ mailsTo(email).lastOrNull { it.subject.contains(subject) } }, { it != null })!!

    @Test
    fun `a guest's ticket is mailed to the shop, and the guest gets a confirmation with the link but not their own text`() {
        val email = "vraag-${word.lowercase()}@example.com"
        val ticket = guestTicket(email)
        val number = ticket["number"].asText()
        assertTrue(number.matches(Regex("T-[2-9A-Z]{6}")), number)
        assertEquals("OPEN", ticket["status"].asText())
        assertEquals("PT-ABC123", ticket["orderNumber"].asText())

        val toShop = waitForMail("shop@example.com", "Nieuw ticket $number")
        assertEquals(email, (toShop.replyTo.first() as InternetAddress).address)
        val shopText = plainText(toShop)!!
        assertContains(shopText, "Bestelnummer: PT-ABC123")
        assertContains(shopText, "Wanneer komt mijn pakket?")
        assertContains(shopText, "/admin/tickets/$number")

        val confirmation = waitForMail(email, "We hebben je vraag ontvangen (ticket $number)")
        val confirmationText = plainText(confirmation)!!
        assertContains(confirmationText, "/klantenservice/ticket/${ticket["token"].asText()}")
        assertFalse(confirmationText.contains("Wanneer komt mijn pakket"), "the visitor's own text never goes to the address they typed")
        assertFalse(confirmationText.contains("Levering"), "nor the subject")

        mockMvc.perform(get("/v1/support/tickets/${ticket["token"].asText()}")).andExpect(status().isOk)
        mockMvc.perform(get("/v1/support/tickets/not-a-real-token")).andExpect(status().isNotFound)
    }

    @Test
    fun `the shop answers in admin, the customer gets it by mail, and writing again opens the ticket again`() {
        val email = "volg-${word.lowercase()}@example.com"
        val ticket = guestTicket(email)
        val number = ticket["number"].asText()
        val token = ticket["token"].asText()

        val listed = mockMvc.perform(get("/v1/admin/tickets?status=OPEN").cookie(admin)).andExpect(status().isOk).json()
        assertTrue(listed["tickets"].any { it["number"].asText() == number })
        assertTrue(listed["counts"]["OPEN"].asLong() >= 1)

        val answered = mockMvc.perform(
            post("/v1/admin/tickets/$number/messages").cookie(admin).json(mapOf("message" to "Hij gaat morgen op de post.")),
        ).andExpect(status().isOk).json()
        assertEquals("ANSWERED", answered["status"].asText())
        assertEquals("Testgebruiker", answered["messages"][1]["author"].asText())
        val answer = waitForMail(email, "Antwoord op je vraag (ticket $number)")
        assertContains(plainText(answer)!!, "Hij gaat morgen op de post.")
        assertEquals("shop@example.com", (answer.replyTo.first() as InternetAddress).address)

        val customerView = mockMvc.perform(get("/v1/support/tickets/$token")).json()
        assertEquals(listOf(false, true), customerView["messages"].map { it["fromShop"].asBoolean() })
        assertFalse(customerView["messages"][1].has("author"), "customers see the shop, not which admin answered")

        mockMvc.perform(post("/v1/admin/tickets/$number/messages").cookie(admin).json(mapOf("message" to "Fijne dag!", "close" to true)))
            .andExpect(status().isOk)
        assertEquals("CLOSED", mockMvc.perform(get("/v1/support/tickets/$token")).json()["status"].asText())

        val reopened = mockMvc.perform(post("/v1/support/tickets/$token/messages").with(fromRandomIp()).json(mapOf("message" to "Nog één vraag")))
            .andExpect(status().isOk).json()
        assertEquals("OPEN", reopened["status"].asText())
        waitForMail("shop@example.com", "Nieuw bericht bij ticket $number")

        mockMvc.perform(put("/v1/admin/tickets/$number/status").cookie(admin).json(mapOf("status" to "CLOSED")))
            .andExpect(status().isOk)
        mockMvc.perform(put("/v1/admin/tickets/$number/status").cookie(admin).json(mapOf("status" to "ANSWERED")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `a logged-in customer's ticket uses the account's address and shows under my tickets`() {
        val email = uniqueEmail()
        val session = loggedInUser(email = email)
        val ticket = createTicket(
            mapOf("name" to "", "email" to "someone-else@example.com", "subject" to "Account $word", "message" to "Ik kan mijn collectie niet delen."),
            session,
        ).andExpect(status().isCreated).json()
        waitForMail(email, "We hebben je vraag ontvangen")
        assertTrue(mailsTo("someone-else@example.com").none { it.subject.contains(ticket["number"].asText()) })

        val mine = mockMvc.perform(get("/v1/account/tickets").cookie(session)).andExpect(status().isOk).json()
        assertEquals(listOf(ticket["number"].asText()), mine.map { it["number"].asText() })
        val other = mockMvc.perform(get("/v1/account/tickets").cookie(loggedInUser())).json()
        assertEquals(0, other.size())
        mockMvc.perform(get("/v1/account/tickets")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `only admins see tickets, bots and short messages are turned away, and the chat goes along when asked`() {
        mockMvc.perform(get("/v1/admin/tickets").cookie(loggedInUser())).andExpect(status().isForbidden)
        createTicket(mapOf("name" to "Bot", "email" to "bot@example.com", "subject" to "Hi", "message" to "Koop nu goedkope dingen", "website" to "spam.example"))
            .andExpect(status().isBadRequest)
        createTicket(mapOf("name" to "Misty", "email" to "misty@example.com", "subject" to "Hoi", "message" to "Kort"))
            .andExpect(status().isBadRequest)
        createTicket(mapOf("name" to "Misty", "email" to "geen-adres", "subject" to "Hoi", "message" to "Een lange genoeg vraag"))
            .andExpect(status().isBadRequest)

        val chat = listOf(
            mapOf("role" to "user", "text" to "Waar is mijn pakket?"),
            mapOf("role" to "assistant", "text" to "Dat kan ik niet zien. Maak een ticket."),
        )
        val ticket = guestTicket("chat-${word.lowercase()}@example.com", extra = mapOf("chat" to chat))
        val body = mockMvc.perform(get("/v1/admin/tickets/${ticket["number"].asText()}").cookie(admin)).json()["messages"][0]["body"].asText()
        assertContains(body, "--- Gesprek met de assistent ---")
        assertContains(body, "Klant: Waar is mijn pakket?")
        assertContains(body, "Assistent: Dat kan ik niet zien.")
    }

    @Test
    fun `the chatbot answers from the info pages with the small model, and counts apart from the hero slides`() {
        assertTrue(mockMvc.perform(get("/v1/support/chat")).json()["available"].asBoolean())
        val before = aiUsageRepository.findAll().count { it.kind == "CHAT" }

        val answer = mockMvc.perform(
            post("/v1/support/chat").with(fromRandomIp()).json(mapOf("messages" to listOf(mapOf("role" to "user", "text" to "Hoe snel versturen jullie?")))),
        ).andExpect(status().isOk).json()
        assertEquals("Bestellingen gaan binnen 1 werkdag op de post.", answer["answer"].asText())
        assertFalse(answer["suggestTicket"].asBoolean())

        assertEquals("gpt-6-luna", fakeAi.models.last())
        val instructions = fakeAi.instructions.last()
        assertContains(instructions, "Zeg nooit hoeveel stuks")
        assertContains(instructions, "(/info/verzending)")
        assertFalse(instructions.contains("(/info/privacybeleid)"), "draft pages stay out")
        assertContains(fakeAi.textInputs.last(), "Klant: Hoe snel versturen jullie?")
        assertEquals(before + 1, aiUsageRepository.findAll().count { it.kind == "CHAT" })

        mockMvc.perform(post("/v1/support/chat").with(fromRandomIp()).json(mapOf("messages" to listOf(mapOf("role" to "assistant", "text" to "Hoi")))))
            .andExpect(status().isBadRequest)

        fakeAi.configuredInTest = false
        assertFalse(mockMvc.perform(get("/v1/support/chat")).json()["available"].asBoolean())
        mockMvc.perform(post("/v1/support/chat").with(fromRandomIp()).json(mapOf("messages" to listOf(mapOf("role" to "user", "text" to "Hallo?")))))
            .andExpect(status().isServiceUnavailable)
    }
}
