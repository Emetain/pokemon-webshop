package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.AiUsage
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.repository.AiUsageRepository
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.fasterxml.jackson.databind.JsonNode
import jakarta.mail.Message
import jakarta.mail.internet.MimeMessage
import jakarta.servlet.http.Cookie
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.Duration
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The main flows of phase 8, from the customer's side and the admin's. */
class ExtrasIntegrationTest : ShopTestSupport() {

    @Autowired
    private lateinit var gateway: FakePaymentGateway

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var fakeAi: FakeAiClient

    @Autowired
    private lateinit var aiUsageRepository: AiUsageRepository

    @Autowired
    private lateinit var clock: Clock

    private val address = mapOf(
        "street" to "Dorpsstraat",
        "houseNumber" to "12a",
        "postcode" to "1421ab",
        "city" to "Uithoorn",
        "country" to "NL",
    )

    @BeforeEach
    fun reset() {
        gateway.reset()
        fakeAi.configuredInTest = true
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    private fun guestCart(productId: Long, quantity: Int = 1): Cookie {
        val result = mockMvc.perform(post("/v1/cart/items").json(mapOf("productId" to productId, "quantity" to quantity)))
            .andExpect(status().isOk).andReturn()
        return requireNotNull(result.response.getCookie("cart"))
    }

    private fun checkout(cart: Cookie? = null, session: Cookie? = null, email: String = "ash-${word.lowercase()}@example.com", code: String? = null): ResultActions {
        val body = mapOf("address" to address, "name" to "Ash Ketchum", "email" to email, "acceptTerms" to true, "discountCode" to code)
        val request = post("/v1/checkout").with(fromRandomIp()).json(body)
        return mockMvc.perform(if (session != null) request.cookie(session) else request.cookie(cart!!))
    }

    private fun pay(orderNumber: String) {
        val sessionId = FakePaymentGateway.sessionId(orderNumber)
        gateway.paid += sessionId
        val (payload, signature) = FakePaymentGateway.signedEvent("checkout.session.completed", sessionId, "evt_${System.nanoTime()}")
        mockMvc.perform(
            post("/v1/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(payload).header("Stripe-Signature", signature),
        ).andExpect(status().isOk)
    }

    /** Products sold here would count as bestsellers in other tests: take them out of the shop afterwards. */
    private fun archive(productId: Long) {
        mockMvc.perform(post("/v1/admin/products/$productId/archive").cookie(admin)).andExpect(status().isOk)
    }

    private fun createCode(body: Map<String, Any?>): JsonNode =
        mockMvc.perform(post("/v1/admin/discount-codes").cookie(admin).json(body)).andExpect(status().isCreated).json()

    private fun mailsTo(email: String): List<MimeMessage> =
        mailSender.sent.filter { it.getRecipients(Message.RecipientType.TO).first().toString() == email }

    private fun waitForMail(email: String, subject: String): MimeMessage =
        await().atMost(Duration.ofSeconds(5)).until({ mailsTo(email).lastOrNull { it.subject.contains(subject) } }, { it != null })!!

    // --- discount codes --------------------------------------------------------------------------------------------

    @Test
    fun `a discount code shows in the cart, goes into the order and to Stripe, and counts as used once paid`() {
        val code = "WELKOM${word.uppercase().take(6)}"
        createCode(mapOf("code" to code.lowercase(), "kind" to "PERCENTAGE", "percent" to 10, "minimumOrderCents" to 5000, "games" to listOf("pokemon")))
        val productId = stocked(quantity = 3)["id"].asLong()
        val cart = guestCart(productId)

        val quoted = mockMvc.perform(get("/v1/cart?country=NL&discountCode= ${code.lowercase()} ").cookie(cart)).andExpect(status().isOk).json()
        assertEquals(9995, quoted["subtotalCents"].asLong())
        // 10% of 99,95 is 9,995: half up to 10,00.
        assertEquals(1000, quoted["discountCents"].asLong())
        assertTrue(quoted["discount"]["applied"].asBoolean())
        assertEquals(0, quoted["shipping"]["priceCents"].asLong(), "89,95 after the discount is still over the free shipping threshold")
        assertEquals(8995, quoted["totalCents"].asLong())

        val number = checkout(cart, code = code).andExpect(status().isCreated).json()["orderNumber"].asText()
        val order = requireNotNull(orderRepository.findByNumber(number))
        assertEquals(1000, order.discountCents)
        assertEquals(code, order.discountCodeText)
        assertEquals(8995, order.totalCents)
        assertEquals(1000, gateway.sessions.getValue(FakePaymentGateway.sessionId(number)).discount?.amountCents)

        pay(number)
        assertEquals(OrderStatus.PAID, orderRepository.findByNumber(number)!!.status, "the paid amount matches the order")
        mockMvc.perform(get("/v1/admin/discount-codes").cookie(admin))
            .andExpect(jsonPath("$[?(@.code == '$code')].paidUses").value(1))
            .andExpect(jsonPath("$[?(@.code == '$code')].givenCents").value(1000))
        val mail = waitForMail("ash-${word.lowercase()}@example.com", "Bedankt voor je bestelling")
        assertContains(plainText(mail)!!, "Korting ($code): −€ 10,00")
        archive(productId)
    }

    @Test
    fun `a code that doesn't apply says why in the cart, and checkout refuses it`() {
        val code = "MAGIC${word.uppercase().take(6)}"
        createCode(mapOf("code" to code, "kind" to "FIXED_AMOUNT", "amountCents" to 500, "games" to listOf("magic")))
        val cart = guestCart(stocked(quantity = 3)["id"].asLong())

        val quoted = mockMvc.perform(get("/v1/cart?discountCode=$code").cookie(cart)).andExpect(status().isOk).json()
        assertEquals(0, quoted["discountCents"].asLong())
        assertFalse(quoted["discount"]["applied"].asBoolean())
        assertEquals("Deze kortingscode geldt niet voor de producten in je bestelling.", quoted["discount"]["message"].asText())

        checkout(cart, code = code).andExpect(status().isConflict).andExpect(jsonPath("$.error").value("discount_invalid"))
        checkout(cart, code = "BESTAATNIET").andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Deze kortingscode is niet (meer) geldig."))
    }

    @Test
    fun `the last use of a code goes to one checkout, and once per customer goes by email for guests`() {
        val single = "EENMALIG${word.uppercase().take(6)}"
        createCode(mapOf("code" to single, "kind" to "FIXED_AMOUNT", "amountCents" to 500, "maxUses" to 1))
        val product = stocked(quantity = 10)["id"].asLong()

        checkout(guestCart(product), email = "a-$word@example.com", code = single).andExpect(status().isCreated)
        checkout(guestCart(product), email = "b-$word@example.com", code = single).andExpect(status().isConflict)

        val once = "PERKLANT${word.uppercase().take(6)}"
        createCode(mapOf("code" to once, "kind" to "PERCENTAGE", "percent" to 5, "oncePerCustomer" to true))
        checkout(guestCart(product), email = "c-$word@example.com", code = once).andExpect(status().isCreated)
        checkout(guestCart(product), email = "C-$word@Example.com", code = once).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Je hebt deze kortingscode al gebruikt."))
        checkout(guestCart(product), email = "d-$word@example.com", code = once).andExpect(status().isCreated)
    }

    // --- mystery boxes ---------------------------------------------------------------------------------------------

    @Test
    fun `a batch fills the stock, the page shows the chances, a payment draws boxes and a cancel puts them back`() {
        val box = createProduct(name = "$word Mystery Box", type = "MYSTERY_BOX", priceCents = 2500)
        val boxId = box["id"].asLong()
        mockMvc.perform(
            post("/v1/admin/products/$boxId/mystery-batches").cookie(admin).json(
                mapOf(
                    "name" to "Batch 1",
                    "prizes" to listOf(
                        mapOf("name" to "Grote hit", "valueCents" to 15000, "count" to 1),
                        mapOf("name" to "Drie boosters", "valueCents" to 1500, "count" to 2),
                    ),
                ),
            ),
        ).andExpect(status().isCreated)
        assertEquals(3, mockMvc.perform(get("/v1/admin/products/$boxId").cookie(admin)).json()["stock"]["available"].asInt())

        val page = mockMvc.perform(get("/v1/products/${box["slug"].asText()}")).andExpect(status().isOk).json()["mysteryBox"]
        // Only chances: never how many boxes there are or are left.
        for (field in listOf("remaining", "boxCount")) assertFalse(page.has(field), "no $field on the product page")
        assertFalse(page["prizes"][0].has("remaining") || page["prizes"][0].has("count"))
        assertEquals(listOf(33.3, 66.7), page["prizes"].map { it["chance"].asDouble() })
        assertEquals(1500, page["minValueCents"].asLong())
        assertEquals(6000, page["averageValueCents"].asLong())

        val number = checkout(guestCart(boxId, 2)).andExpect(status().isCreated).json()["orderNumber"].asText()
        pay(number)
        val adminOrder = mockMvc.perform(get("/v1/admin/orders/$number").cookie(admin)).andExpect(status().isOk).json()
        val drawn = adminOrder["order"]["lines"][0]["mysteryBoxes"].map { it.asText() }
        assertEquals(2, drawn.size)
        assertTrue(drawn.all { it in setOf("Grote hit", "Drie boosters") })
        val token = requireNotNull(orderRepository.findByNumber(number)).accessToken
        val guestView = mockMvc.perform(get("/v1/orders/$number?token=$token")).andExpect(status().isOk).json()
        assertEquals(0, guestView["lines"][0]["mysteryBoxes"].size(), "a surprise until the parcel ships")

        val batch = mockMvc.perform(get("/v1/admin/products/$boxId/mystery-batches").cookie(admin)).json()[0]
        assertEquals(1, batch["remaining"].asInt())
        assertEquals(2, batch["draws"].size())
        mockMvc.perform(delete("/v1/admin/mystery-batches/${batch["id"].asLong()}").cookie(admin)).andExpect(status().isBadRequest)

        mockMvc.perform(post("/v1/admin/orders/$number/cancel").cookie(admin).json(mapOf("note" to "Test")))
            .andExpect(status().isOk)
        val afterCancel = mockMvc.perform(get("/v1/admin/products/$boxId/mystery-batches").cookie(admin)).json()[0]
        assertEquals(3, afterCancel["remaining"].asInt(), "the boxes are back in the batch")
        assertEquals(0, afterCancel["draws"].size())
    }

    // --- reviews ---------------------------------------------------------------------------------------------------

    @Test
    fun `only a buyer writes a review, and it shows after approval with the first name only`() {
        val product = stocked(quantity = 5)
        val slug = product["slug"].asText()
        val buyerEmail = uniqueEmail()
        userService.register(buyerEmail, password, "Misty Waterflower", Role.CUSTOMER)
        val buyer = login(buyerEmail).sessionCookie()
        val stranger = loggedInUser()
        val review = mapOf("rating" to 5, "title" to "Top", "text" to "Snel geleverd en goed verpakt.")

        mockMvc.perform(put("/v1/products/$slug/reviews").cookie(stranger).with(fromRandomIp()).json(review)).andExpect(status().isForbidden)

        mockMvc.perform(post("/v1/cart/items").cookie(buyer).json(mapOf("productId" to product["id"].asLong(), "quantity" to 1)))
            .andExpect(status().isOk)
        pay(checkout(session = buyer).andExpect(status().isCreated).json()["orderNumber"].asText())

        val written = mockMvc.perform(put("/v1/products/$slug/reviews").cookie(buyer).with(fromRandomIp()).json(review))
            .andExpect(status().isOk).json()
        assertEquals("PENDING", written["own"]["status"].asText())
        assertEquals(0, written["count"].asInt(), "not public before approval")

        val pending = mockMvc.perform(get("/v1/admin/reviews?status=PENDING").cookie(admin)).json()["reviews"]
            .first { it["productSlug"].asText() == slug }
        mockMvc.perform(post("/v1/admin/reviews/${pending["id"].asLong()}/approve").cookie(admin)).andExpect(status().isOk)

        mockMvc.perform(get("/v1/products/$slug/reviews"))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.averageRating").value(5.0))
            .andExpect(jsonPath("$.reviews[0].authorName").value("Misty"))
        mockMvc.perform(get("/v1/products/$slug")).andExpect(jsonPath("$.reviewCount").value(1))
        archive(product["id"].asLong())
    }

    // --- newsletter, info pages ---------------------------------------------------------------------------------

    @Test
    fun `newsletter signup only counts after the link in the mail, and the export has confirmed addresses only`() {
        val email = "news-${word}@example.com"
        mockMvc.perform(post("/v1/newsletter").with(fromRandomIp()).json(mapOf("email" to email))).andExpect(status().isNoContent)
        assertFalse(mockMvc.perform(get("/v1/admin/newsletter/export").cookie(admin)).andReturn().response.contentAsString.contains(email))

        val text = plainText(waitForMail(email, "Bevestig"))!!
        val confirm = Regex("bevestigen\\?token=([A-Za-z0-9_-]+)").find(text)!!.groupValues[1]
        val unsubscribe = Regex("afmelden\\?token=([A-Za-z0-9_-]+)").find(text)!!.groupValues[1]
        mockMvc.perform(post("/v1/newsletter/confirm").with(fromRandomIp()).json(mapOf("token" to confirm))).andExpect(status().isNoContent)
        assertContains(mockMvc.perform(get("/v1/admin/newsletter/export").cookie(admin)).andReturn().response.contentAsString, email)

        // Signing up again sends nothing: the answer is the same for a known address.
        mockMvc.perform(post("/v1/newsletter").with(fromRandomIp()).json(mapOf("email" to email.uppercase()))).andExpect(status().isNoContent)
        mockMvc.perform(post("/v1/newsletter/unsubscribe").with(fromRandomIp()).json(mapOf("token" to unsubscribe))).andExpect(status().isNoContent)
        assertFalse(mockMvc.perform(get("/v1/admin/newsletter/export").cookie(admin)).andReturn().response.contentAsString.contains(email))
    }

    @Test
    fun `info pages exist from the start, the legal ones as drafts, and the admin edits them`() {
        mockMvc.perform(get("/v1/pages/privacybeleid")).andExpect(status().isOk).andExpect(jsonPath("$.draft").value(true))
        mockMvc.perform(get("/v1/pages/verzending")).andExpect(jsonPath("$.draft").value(false))
        mockMvc.perform(put("/v1/admin/pages/privacybeleid").cookie(admin).json(mapOf("title" to "Privacy", "body" to "Gecontroleerd.", "draft" to false)))
            .andExpect(status().isOk)
        mockMvc.perform(get("/v1/pages/privacybeleid")).andExpect(jsonPath("$.body").value("Gecontroleerd.")).andExpect(jsonPath("$.draft").value(false))
        mockMvc.perform(post("/v1/admin/pages/privacybeleid/reset").cookie(admin)).andExpect(jsonPath("$.draft").value(true))
        mockMvc.perform(get("/v1/pages/bestaat-niet")).andExpect(status().isNotFound)
    }

    // --- release calendar, news ------------------------------------------------------------------------------------

    @Test
    fun `a release shows its pre-orders on the calendar and gets a hero slide in one click`() {
        val preOrder = createProduct(name = "$word Elite Trainer Box", type = "ELITE_TRAINER_BOX", extra = mapOf("preOrder" to true, "releaseDate" to "2099-05-30"))
        val release = mockMvc.perform(
            post("/v1/admin/releases").cookie(admin).json(
                mapOf("game" to "pokemon", "name" to "$word Rivals", "releaseDate" to "2099-05-30", "productIds" to listOf(preOrder["id"].asLong())),
            ),
        ).andExpect(status().isCreated).json()

        val calendar = mockMvc.perform(get("/v1/releases?game=pokemon")).andExpect(status().isOk).json()
        val shown = calendar["upcoming"].first { it["id"].asLong() == release["id"].asLong() }
        assertEquals(preOrder["id"].asLong(), shown["preOrders"][0]["id"].asLong())
        assertTrue(mockMvc.perform(get("/v1/pre-orders")).json().any { it["id"].asLong() == preOrder["id"].asLong() })

        val slide = mockMvc.perform(post("/v1/admin/releases/${release["id"].asLong()}/hero-slide").cookie(admin))
            .andExpect(status().isCreated).json()
        assertEquals("/releasekalender#release-${release["id"].asLong()}", slide["linkUrl"].asText())
        assertEquals("pokemon", slide["game"].asText())
        assertContains(slide["text"].asText(), "30 mei 2099")
    }

    @Test
    fun `news shows once published, and a planned article waits for its moment`() {
        val title = "$word nieuws"
        val article = mockMvc.perform(post("/v1/admin/news").cookie(admin).json(mapOf("title" to title, "body" to "## Kop\nTekst", "published" to false)))
            .andExpect(status().isCreated).json()
        val slug = article["slug"].asText()
        mockMvc.perform(get("/v1/news/$slug")).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/news/$slug").cookie(admin)).andExpect(status().isOk).andExpect(jsonPath("$.published").value(false))

        mockMvc.perform(put("/v1/admin/news/${article["id"].asLong()}").cookie(admin).json(mapOf("title" to title, "body" to "Tekst", "published" to true)))
            .andExpect(status().isOk)
        mockMvc.perform(get("/v1/news/$slug")).andExpect(status().isOk)
        assertTrue(mockMvc.perform(get("/v1/news?size=24")).json()["articles"].any { it["slug"].asText() == slug })

        val planned = clock.instant().plus(Duration.ofDays(2)).toString()
        mockMvc.perform(put("/v1/admin/news/${article["id"].asLong()}").cookie(admin).json(mapOf("title" to title, "body" to "Tekst", "published" to true, "publishedAt" to planned)))
            .andExpect(status().isOk)
        mockMvc.perform(get("/v1/news/$slug")).andExpect(status().isNotFound)
    }

    // --- hero slides written by AI ---------------------------------------------------------------------------------

    @Test
    fun `AI writes a slide that starts switched off, with an image without characters, and stops at the monthly limit`() {
        mockMvc.perform(get("/v1/admin/hero-slides/ai").cookie(admin)).andExpect(jsonPath("$.available").value(true))

        val slide = mockMvc.perform(
            post("/v1/admin/hero-slides/ai").cookie(admin).json(mapOf("prompt" to "pre-order Destined Rivals, release 30 mei", "withImage" to true)),
        ).andExpect(status().isCreated).json()
        assertEquals("Destined Rivals in pre-order", slide["title"].asText())
        assertFalse(slide["active"].asBoolean(), "nothing goes live before the admin approves it")
        assertTrue(slide["aiGenerated"].asBoolean())
        assertEquals("/pre-order", slide["linkUrl"].asText())
        assertNotNull(slide["imageUrl"].textValue())
        assertContains(fakeAi.imagePrompts.last(), "do not show any Pokémon or other characters")
        assertContains(fakeAi.textInputs.last(), "pre-order Destined Rivals")
        mockMvc.perform(get("/v1/shop/hero-slides"))
            .andExpect(jsonPath("$[?(@.title == 'Destined Rivals in pre-order')]").isEmpty)

        // Spend the rest of the month's $5.
        val spent = aiUsageRepository.save(AiUsage("TEXT", "test", 0, 0, 5_000_000, clock.instant()))
        try {
            mockMvc.perform(post("/v1/admin/hero-slides/ai").cookie(admin).json(mapOf("prompt" to "Nog een slide graag")))
                .andExpect(status().isTooManyRequests)
                .andExpect(jsonPath("$.error").value("ai_limit"))
        } finally {
            aiUsageRepository.delete(spent)
        }
    }

    @Test
    fun `without an OpenAI key the AI is not available and everything else works`() {
        fakeAi.configuredInTest = false
        mockMvc.perform(get("/v1/admin/hero-slides/ai").cookie(admin)).andExpect(jsonPath("$.available").value(false))
        mockMvc.perform(post("/v1/admin/hero-slides/ai").cookie(admin).json(mapOf("prompt" to "Een slide over drops")))
            .andExpect(status().isServiceUnavailable)
        mockMvc.perform(get("/v1/admin/hero-slides").cookie(admin)).andExpect(status().isOk)
    }
}
