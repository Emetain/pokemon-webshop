package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.OrderStockState
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.emetain.pokemonwebshop.service.CheckoutJobs
import com.emetain.pokemonwebshop.service.payment.ExpireResult
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The whole road from cart to paid order, with Stripe replaced by [FakePaymentGateway]. */
class CheckoutIntegrationTest : ShopTestSupport() {

    @Autowired
    private lateinit var gateway: FakePaymentGateway

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var jobs: CheckoutJobs

    private val address = mapOf(
        "street" to "Dorpsstraat",
        "houseNumber" to "12a",
        "postcode" to "1421ab",
        "city" to "Uithoorn",
        "country" to "NL",
    )

    @BeforeEach
    fun resetGateway() = gateway.reset()

    // --- helpers ---------------------------------------------------------------------------------------------------

    private fun MockHttpServletRequestBuilder.withCart(cart: Cookie?) = if (cart != null) cookie(cart) else this

    private fun addToCart(productId: Long, quantity: Int = 1, cart: Cookie? = null, session: Cookie? = null): ResultActions {
        val request = post("/v1/cart/items").json(mapOf("productId" to productId, "quantity" to quantity))
        return mockMvc.perform(if (session != null) request.cookie(session) else request.withCart(cart))
    }

    /** Adds a product as a guest and returns the cart cookie the shop sets. */
    private fun guestCart(productId: Long, quantity: Int = 1): Cookie {
        val result = addToCart(productId, quantity).andExpect(status().isOk).andReturn()
        return requireNotNull(result.response.getCookie("cart")) { "No cart cookie" }
    }

    private fun cart(cart: Cookie? = null, session: Cookie? = null, country: String = "NL"): JsonNode {
        val request = get("/v1/cart?country=$country")
        return mockMvc.perform(if (session != null) request.cookie(session) else request.withCart(cart)).andExpect(status().isOk).json()
    }

    private fun checkout(cart: Cookie? = null, session: Cookie? = null, extra: Map<String, Any?> = emptyMap()): ResultActions {
        val body = mapOf(
            "address" to address,
            "name" to "Ash Ketchum",
            "email" to "ash-${word.lowercase()}@example.com",
            "acceptTerms" to true,
        ) + extra
        val request = post("/v1/checkout").with(fromRandomIp()).json(body)
        return mockMvc.perform(if (session != null) request.cookie(session) else request.withCart(cart))
    }

    private fun stock(productId: Long): JsonNode =
        mockMvc.perform(get("/v1/admin/products/$productId").cookie(admin)).andExpect(status().isOk).json()["stock"]

    private fun webhook(type: String, sessionId: String, eventId: String = "evt_${System.nanoTime()}"): ResultActions {
        val (payload, signature) = FakePaymentGateway.signedEvent(type, sessionId, eventId)
        return mockMvc.perform(
            post("/v1/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(payload).header("Stripe-Signature", signature),
        )
    }

    /** What Stripe does once the customer pays: the session is paid, and the webhook arrives. */
    private fun pay(orderNumber: String): ResultActions {
        val sessionId = FakePaymentGateway.sessionId(orderNumber)
        gateway.paid += sessionId
        return webhook("checkout.session.completed", sessionId)
    }

    private fun order(number: String) = requireNotNull(orderRepository.findByNumber(number))

    private fun mailsTo(email: String): List<MimeMessage> =
        mailSender.sent.filter { it.getRecipients(Message.RecipientType.TO).first().toString() == email }

    private fun waitForMail(email: String, subject: String): MimeMessage =
        await().atMost(Duration.ofSeconds(5)).until({ mailsTo(email).lastOrNull { it.subject.contains(subject) } }, { it != null })!!

    // --- tests -----------------------------------------------------------------------------------------------------

    @Test
    fun `a guest pays and the webhook makes the order paid, takes the stock and sends the confirmation`() {
        val product = stocked(quantity = 5)
        val productId = product["id"].asLong()
        val cartCookie = guestCart(productId, 2)

        val cart = cart(cartCookie)
        assertEquals(2, cart["itemCount"].asInt())
        assertEquals(19990, cart["subtotalCents"].asLong())
        assertEquals(0, cart["shipping"]["priceCents"].asLong(), "free from €75 in the Netherlands")
        assertEquals(listOf("ideal", "card"), cart["paymentMethods"].map { it.asText() })

        val started = checkout(cartCookie).andExpect(status().isCreated).json()
        val number = started["orderNumber"].asText()
        val token = started["accessToken"].asText()
        assertTrue(started["redirectUrl"].asText().startsWith("https://checkout.stripe.test/"))
        assertEquals(2, stock(productId)["reserved"].asInt(), "checkout reserves the stock")
        assertEquals(OrderStatus.PENDING_PAYMENT, order(number).status)

        val request = gateway.sessions.getValue(FakePaymentGateway.sessionId(number))
        assertEquals(listOf("ideal", "card"), request.paymentMethods)
        assertContains(request.successUrl, "/bestelling/bedankt?order=$number&token=$token")
        assertEquals(19990, request.lines.sumOf { it.unitAmountCents * it.quantity })

        // The success page before the webhook: still waiting, and nothing changes by looking.
        val before = mockMvc.perform(get("/v1/orders/$number?token=$token")).andExpect(status().isOk).json()
        assertEquals("PENDING_PAYMENT", before["status"].asText())

        pay(number).andExpect(status().isOk)

        val paid = order(number)
        assertEquals(OrderStatus.PAID, paid.status)
        assertEquals(OrderStockState.SOLD, paid.stockState)
        assertEquals("ideal", paid.paymentMethod)
        assertEquals("pi_cs_test_$number", paid.stripePaymentIntentId)
        val after = stock(productId)
        assertEquals(3, after["onHand"].asInt())
        assertEquals(0, after["reserved"].asInt())
        assertEquals(0, cart(cartCookie)["itemCount"].asInt(), "what was bought leaves the cart")

        val mail = waitForMail("ash-${word.lowercase()}@example.com", "Bedankt voor je bestelling $number")
        assertContains(plainText(mail)!!, "2 × $word Booster Box")
        assertContains(plainText(mail)!!, "/bestelling/$number?token=$token")
    }

    @Test
    fun `the same webhook twice, or a refreshed success page, never changes the stock twice`() {
        val productId = stocked(quantity = 4)["id"].asLong()
        val cartCookie = guestCart(productId, 1)
        val started = checkout(cartCookie).andExpect(status().isCreated).json()
        val number = started["orderNumber"].asText()
        val token = started["accessToken"].asText()

        pay(number).andExpect(status().isOk)
        webhook("checkout.session.completed", FakePaymentGateway.sessionId(number)).andExpect(status().isOk)
        webhook("checkout.session.async_payment_succeeded", FakePaymentGateway.sessionId(number)).andExpect(status().isOk)
        repeat(3) {
            val view = mockMvc.perform(get("/v1/orders/$number?token=$token")).andExpect(status().isOk).json()
            assertEquals("PAID", view["status"].asText())
        }

        assertEquals(3, stock(productId)["onHand"].asInt())
        assertEquals(1, orderRepository.findAll().count { it.cartId == order(number).cartId && it.paidAt != null })
        val email = "ash-${word.lowercase()}@example.com"
        waitForMail(email, "Bedankt")
        Thread.sleep(300)
        assertEquals(1, mailsTo(email).count { it.subject.contains("Bedankt") })
    }

    @Test
    fun `two deliveries of the same event at the same moment sell the stock once`() {
        val productId = stocked(quantity = 3)["id"].asLong()
        val number = checkout(guestCart(productId, 2)).andExpect(status().isCreated).json()["orderNumber"].asText()
        val sessionId = FakePaymentGateway.sessionId(number)
        gateway.paid += sessionId

        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val results = (1..2).map {
            pool.submit<Int> {
                start.await()
                webhook("checkout.session.completed", sessionId, "evt_same").andReturn().response.status
            }
        }
        start.countDown()
        val statuses = results.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(listOf(200, 200), statuses)
        val after = stock(productId)
        assertEquals(1, after["onHand"].asInt())
        assertEquals(0, after["reserved"].asInt())
    }

    @Test
    fun `a webhook without Stripe's signature changes nothing`() {
        val productId = stocked(quantity = 2)["id"].asLong()
        val number = checkout(guestCart(productId)).andExpect(status().isCreated).json()["orderNumber"].asText()
        val sessionId = FakePaymentGateway.sessionId(number)
        gateway.paid += sessionId

        val (payload, _) = FakePaymentGateway.signedEvent("checkout.session.completed", sessionId)
        val (_, forged) = FakePaymentGateway.signedEvent("checkout.session.completed", sessionId, secret = "whsec_attacker")
        mockMvc.perform(post("/v1/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(payload).header("Stripe-Signature", forged))
            .andExpect(status().isBadRequest)
        mockMvc.perform(post("/v1/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isBadRequest)

        assertEquals(OrderStatus.PENDING_PAYMENT, order(number).status)
        assertEquals(1, stock(productId)["reserved"].asInt())
    }

    @Test
    fun `a session that isn't paid doesn't make the order paid, whatever the event says`() {
        val productId = stocked(quantity = 2)["id"].asLong()
        val number = checkout(guestCart(productId)).andExpect(status().isCreated).json()["orderNumber"].asText()

        webhook("checkout.session.completed", FakePaymentGateway.sessionId(number)).andExpect(status().isOk)

        assertEquals(OrderStatus.PENDING_PAYMENT, order(number).status)
        assertNull(order(number).paidAt)
    }

    @Test
    fun `an abandoned checkout gives its stock back once the reservation runs out`() {
        val productId = stocked(quantity = 1)["id"].asLong()
        val number = checkout(guestCart(productId)).andExpect(status().isCreated).json()["orderNumber"].asText()
        assertEquals(0, stock(productId)["available"].asInt())

        jobs.releaseExpiredReservations()
        assertEquals(OrderStatus.PENDING_PAYMENT, order(number).status, "not before the 15 minutes are over")

        expireReservation(number)
        jobs.releaseExpiredReservations()

        val cancelled = order(number)
        assertEquals(OrderStatus.CANCELLED, cancelled.status)
        assertEquals(OrderStockState.RELEASED, cancelled.stockState)
        assertTrue(FakePaymentGateway.sessionId(number) in gateway.expired, "the payment page is closed first")
        val after = stock(productId)
        assertEquals(0, after["reserved"].asInt())
        assertEquals(1, after["available"].asInt())
    }

    @Test
    fun `a checkout paid just before the reservation ran out keeps its stock`() {
        val productId = stocked(quantity = 1)["id"].asLong()
        val number = checkout(guestCart(productId)).andExpect(status().isCreated).json()["orderNumber"].asText()
        gateway.expireResults[FakePaymentGateway.sessionId(number)] = ExpireResult.COMPLETE

        expireReservation(number)
        jobs.releaseExpiredReservations()

        assertEquals(OrderStatus.PENDING_PAYMENT, order(number).status)
        assertEquals(1, stock(productId)["reserved"].asInt())
        pay(number).andExpect(status().isOk)
        assertEquals(OrderStatus.PAID, order(number).status)
        assertEquals(0, stock(productId)["onHand"].asInt())
    }

    @Test
    fun `going back from the payment page gives the stock back right away`() {
        val productId = stocked(quantity = 1)["id"].asLong()
        val started = checkout(guestCart(productId)).andExpect(status().isCreated).json()
        val number = started["orderNumber"].asText()

        mockMvc.perform(post("/v1/orders/$number/cancel").json(mapOf("token" to "wrong"))).andExpect(status().isNotFound)
        val view = mockMvc.perform(post("/v1/orders/$number/cancel").json(mapOf("token" to started["accessToken"].asText())))
            .andExpect(status().isOk).json()

        assertEquals("CANCELLED", view["status"].asText())
        assertEquals(1, stock(productId)["available"].asInt())
    }

    @Test
    fun `checking out again replaces the earlier attempt instead of blocking your own stock`() {
        val productId = stocked(quantity = 1)["id"].asLong()
        val cartCookie = guestCart(productId)
        val first = checkout(cartCookie).andExpect(status().isCreated).json()["orderNumber"].asText()

        val second = checkout(cartCookie).andExpect(status().isCreated).json()["orderNumber"].asText()

        assertEquals(OrderStatus.CANCELLED, order(first).status)
        assertEquals(OrderStatus.PENDING_PAYMENT, order(second).status)
        assertEquals(1, stock(productId)["reserved"].asInt())
    }

    @Test
    fun `when stock ran out after adding to the cart, checkout reserves nothing and says what changed`() {
        val productId = stocked(quantity = 3)["id"].asLong()
        val other = stocked(name = "$word Elite Trainer Box", quantity = 5)["id"].asLong()
        val cartCookie = guestCart(productId, 3)
        addToCart(other, 1, cartCookie).andExpect(status().isOk)
        adjustStock(productId, -2, "CORRECTION").andExpect(status().isOk)

        val error = checkout(cartCookie).andExpect(status().isConflict).json()

        assertEquals("cart_changed", error["error"].asText())
        assertContains(error["message"].asText(), "nog maar 1")
        assertEquals(0, stock(other)["reserved"].asInt(), "nothing is reserved when one line fails")
        val cart = cart(cartCookie)
        assertEquals("TOO_MANY", cart["items"].first { it["productId"].asLong() == productId }["problem"].asText())
        assertFalse(cart["canCheckout"].asBoolean())
    }

    @Test
    fun `checkout needs the terms, a name, an email and a valid postcode`() {
        val productId = stocked(quantity = 3)["id"].asLong()
        val cartCookie = guestCart(productId)

        checkout(cartCookie, extra = mapOf("acceptTerms" to false)).andExpect(status().isBadRequest)
        checkout(cartCookie, extra = mapOf("name" to " ")).andExpect(status().isBadRequest)
        checkout(cartCookie, extra = mapOf("email" to "geen-mail")).andExpect(status().isBadRequest)
        checkout(cartCookie, extra = mapOf("address" to address + ("postcode" to "12345"))).andExpect(status().isBadRequest)
        checkout(null).andExpect(status().isBadRequest)

        assertEquals(0, stock(productId)["reserved"].asInt())
    }

    @Test
    fun `when Stripe can't open the payment page, the stock goes back`() {
        val productId = stocked(quantity = 2)["id"].asLong()
        gateway.failCreate = true

        checkout(guestCart(productId)).andExpect(status().isBadGateway)

        assertEquals(0, stock(productId)["reserved"].asInt())
    }

    @Test
    fun `Belgium gets Bancontact and its own shipping costs`() {
        val productId = stocked(quantity = 2, priceCents = 1000)["id"].asLong()
        val cartCookie = guestCart(productId)

        val cart = cart(cartCookie, country = "BE")
        assertEquals(895, cart["shipping"]["priceCents"].asLong())
        assertEquals(listOf("bancontact", "card"), cart["paymentMethods"].map { it.asText() })

        val number = checkout(cartCookie, extra = mapOf("address" to address + mapOf("country" to "BE", "postcode" to "2000")))
            .andExpect(status().isCreated).json()["orderNumber"].asText()
        val order = order(number)
        assertEquals(1895, order.totalCents)
        assertEquals(listOf("bancontact", "card"), gateway.sessions.getValue(FakePaymentGateway.sessionId(number)).paymentMethods)
    }

    @Test
    fun `a guest cart merges into the account cart on login`() {
        val productId = stocked(quantity = 10)["id"].asLong()
        val email = uniqueEmail()
        userService.register(email, password, "Misty")
        val session = login(email).sessionCookie()
        addToCart(productId, 1, session = session).andExpect(status().isOk)

        val cartCookie = guestCart(productId, 2)
        val loggedIn = mockMvc.perform(
            post("/v1/auth/login").with(fromRandomIp()).cookie(cartCookie).json(mapOf("email" to email, "password" to password)),
        ).andExpect(status().isOk).andReturn()

        val cleared = loggedIn.response.getCookies().first { it.name == "cart" }
        assertEquals(0, cleared.maxAge)
        val newSession = loggedIn.response.getCookies().first { it.name == "session" }
        assertEquals(3, cart(session = newSession)["items"].single()["quantity"].asInt())
        assertEquals(0, cart(cartCookie)["itemCount"].asInt(), "the guest cart is gone")
    }

    @Test
    fun `a logged-in customer orders in the profile name and finds the order in the account`() {
        val productId = stocked(quantity = 5)["id"].asLong()
        val email = uniqueEmail()
        userService.register(email, password, "Brock Harrison")
        val session = login(email).sessionCookie()
        addToCart(productId, 1, session = session).andExpect(status().isOk)

        val number = checkout(session = session, extra = mapOf("name" to "Iemand Anders", "email" to "ander@example.com", "saveAddress" to true))
            .andExpect(status().isCreated).json()["orderNumber"].asText()
        val order = order(number)
        assertEquals("Brock Harrison", order.customerName)
        assertEquals(email, order.email)
        assertEquals("1421 AB", order.postcode)

        val list = mockMvc.perform(get("/v1/account/orders").cookie(session)).andExpect(status().isOk).json()
        assertEquals(0, list["totalItems"].asInt(), "an unpaid checkout isn't an order in the account yet")

        pay(number).andExpect(status().isOk)
        val paidList = mockMvc.perform(get("/v1/account/orders").cookie(session)).andExpect(status().isOk).json()
        assertEquals(number, paidList["items"].single()["number"].asText())
        val detail = mockMvc.perform(get("/v1/account/orders/$number").cookie(session)).andExpect(status().isOk).json()
        assertEquals(listOf("PENDING_PAYMENT", "PAID"), detail["timeline"].map { it["status"].asText() })
        assertEquals(1, mockMvc.perform(get("/v1/account/addresses").cookie(session)).andReturn().let {
            objectMapper.readTree(it.response.contentAsString).size()
        })
    }

    @Test
    fun `nobody else can see an order, not even with the number`() {
        val productId = stocked(quantity = 5)["id"].asLong()
        val started = checkout(guestCart(productId)).andExpect(status().isCreated).json()
        val number = started["orderNumber"].asText()
        val stranger = loggedInUser()

        mockMvc.perform(get("/v1/orders/$number")).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/orders/$number?token=nope")).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/orders/$number").cookie(stranger)).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/account/orders/$number").cookie(stranger)).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/orders/$number").cookie(admin)).andExpect(status().isOk)
    }

    @Test
    fun `a guest makes an account after paying and is logged in with the order in it`() {
        val productId = stocked(quantity = 5)["id"].asLong()
        val email = "nieuw-${word.lowercase()}@example.com"
        val started = checkout(guestCart(productId), extra = mapOf("email" to email)).andExpect(status().isCreated).json()
        val number = started["orderNumber"].asText()
        val token = started["accessToken"].asText()

        mockMvc.perform(post("/v1/orders/$number/account").with(fromRandomIp()).json(mapOf("token" to token, "password" to "geheim123")))
            .andExpect(status().isConflict)
        pay(number).andExpect(status().isOk)
        assertTrue(mockMvc.perform(get("/v1/orders/$number?token=$token")).andReturn().let {
            objectMapper.readTree(it.response.contentAsString)["canCreateAccount"].asBoolean()
        })

        val created = mockMvc.perform(post("/v1/orders/$number/account").with(fromRandomIp()).json(mapOf("token" to token, "password" to "geheim123")))
            .andExpect(status().isCreated).andReturn()
        val session = created.response.getCookie("session")!!
        val orders = mockMvc.perform(get("/v1/account/orders").cookie(session)).andExpect(status().isOk).json()
        assertEquals(number, orders["items"].single()["number"].asText())
        mockMvc.perform(post("/v1/orders/$number/account").with(fromRandomIp()).json(mapOf("token" to token, "password" to "geheim123")))
            .andExpect(status().isConflict)
    }

    @Test
    fun `the admin ships with track and trace, and the customer gets the link`() {
        val productId = stocked(quantity = 5)["id"].asLong()
        val number = checkout(guestCart(productId)).andExpect(status().isCreated).json()["orderNumber"].asText()
        pay(number).andExpect(status().isOk)

        mockMvc.perform(post("/v1/admin/orders/$number/status").cookie(admin).json(mapOf("status" to "DELIVERED")))
            .andExpect(status().isBadRequest)
        mockMvc.perform(post("/v1/admin/orders/$number/status").cookie(admin).json(mapOf("status" to "PROCESSING")))
            .andExpect(status().isOk)
        mockMvc.perform(post("/v1/admin/orders/$number/shipment").cookie(admin).json(mapOf("carrier" to "DHL", "code" to "JVGL123", "url" to "javascript:alert(1)")))
            .andExpect(status().isBadRequest)
        val shipped = mockMvc.perform(post("/v1/admin/orders/$number/shipment").cookie(admin).json(mapOf("carrier" to "POSTNL", "code" to "3SABC123")))
            .andExpect(status().isOk).json()

        assertEquals("SHIPPED", shipped["order"]["status"].asText())
        assertEquals("https://jouw.postnl.nl/track-and-trace/3SABC123-NL-1421AB", shipped["order"]["trackingUrl"].asText())
        val mail = waitForMail("ash-${word.lowercase()}@example.com", "onderweg")
        assertContains(plainText(mail)!!, "3SABC123")
        assertContains(plainText(mail)!!, "https://jouw.postnl.nl/track-and-trace/3SABC123-NL-1421AB")

        val delivered = mockMvc.perform(post("/v1/admin/orders/$number/status").cookie(admin).json(mapOf("status" to "DELIVERED")))
            .andExpect(status().isOk).json()
        assertNotNull(delivered["order"]["deliveredAt"].textValue())
    }

    @Test
    fun `cancelling a paid order refunds it at Stripe and puts the stock back`() {
        val productId = stocked(quantity = 5)["id"].asLong()
        val number = checkout(guestCart(productId, 2)).andExpect(status().isCreated).json()["orderNumber"].asText()
        pay(number).andExpect(status().isOk)
        assertEquals(3, stock(productId)["onHand"].asInt())

        gateway.failRefund = true
        mockMvc.perform(post("/v1/admin/orders/$number/cancel").cookie(admin).json(mapOf("note" to "Klant vroeg erom")))
            .andExpect(status().isBadGateway)
        assertEquals(OrderStatus.PAID, order(number).status, "a failed refund changes nothing")

        gateway.failRefund = false
        val cancelled = mockMvc.perform(post("/v1/admin/orders/$number/cancel").cookie(admin).json(mapOf("note" to "Klant vroeg erom")))
            .andExpect(status().isOk).json()

        assertEquals("CANCELLED", cancelled["order"]["status"].asText())
        assertEquals(19990, cancelled["order"]["refundedCents"].asLong())
        assertEquals(19990, gateway.refunds.single { it.paymentIntentId == "pi_cs_test_$number" }.amountCents)
        assertEquals(5, stock(productId)["onHand"].asInt())
        val mail = waitForMail("ash-${word.lowercase()}@example.com", "geannuleerd")
        assertContains(plainText(mail)!!, "€ 199,90 teruggestort")
    }

    @Test
    fun `a partial refund after delivery keeps the order, a full one marks it refunded`() {
        val productId = stocked(quantity = 5, priceCents = 5000)["id"].asLong()
        val number = checkout(guestCart(productId, 2)).andExpect(status().isCreated).json()["orderNumber"].asText()
        pay(number).andExpect(status().isOk)
        mockMvc.perform(post("/v1/admin/orders/$number/shipment").cookie(admin).json(mapOf("carrier" to "POSTNL", "code" to "3SX")))
            .andExpect(status().isOk)

        mockMvc.perform(post("/v1/admin/orders/$number/refund").cookie(admin).json(mapOf("amountCents" to 20000)))
            .andExpect(status().isBadRequest)
        val partial = mockMvc.perform(post("/v1/admin/orders/$number/refund").cookie(admin).json(mapOf("amountCents" to 5000, "restock" to true)))
            .andExpect(status().isOk).json()
        assertEquals("SHIPPED", partial["order"]["status"].asText())
        assertEquals(5000, partial["actions"]["refundableCents"].asLong())
        assertEquals(5, stock(productId)["onHand"].asInt(), "restocked once")

        val full = mockMvc.perform(post("/v1/admin/orders/$number/refund").cookie(admin).json(mapOf("restock" to true)))
            .andExpect(status().isOk).json()
        assertEquals("REFUNDED", full["order"]["status"].asText())
        assertEquals(10000, full["order"]["refundedCents"].asLong())
        assertEquals(5, stock(productId)["onHand"].asInt(), "stock can only go back once")
    }

    @Test
    fun `the admin lists orders by status, and abandoned checkouts only on request`() {
        val productId = stocked(quantity = 5)["id"].asLong()
        val paidNumber = checkout(guestCart(productId)).andExpect(status().isCreated).json()["orderNumber"].asText()
        pay(paidNumber).andExpect(status().isOk)
        val abandoned = checkout(guestCart(productId)).andExpect(status().isCreated).json()["orderNumber"].asText()
        expireReservation(abandoned)
        jobs.releaseExpiredReservations()

        fun numbers(query: String) = mockMvc.perform(get("/v1/admin/orders?q=ash-${word.lowercase()}&$query").cookie(admin))
            .andExpect(status().isOk).json()["items"].map { it["number"].asText() }

        assertEquals(listOf(paidNumber), numbers(""))
        assertEquals(listOf(paidNumber), numbers("status=PAID"))
        assertEquals(listOf(abandoned), numbers("status=ABANDONED"))
        assertEquals(emptyList(), numbers("status=SHIPPED"))
        mockMvc.perform(get("/v1/admin/orders").cookie(loggedInUser())).andExpect(status().isForbidden)
        mockMvc.perform(get("/v1/admin/dashboard").cookie(loggedInUser())).andExpect(status().isForbidden)
        mockMvc.perform(put("/v1/admin/shipping-rates/NL").cookie(loggedInUser()).json(mapOf("priceCents" to 0, "deliveryTime" to "x")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `the dashboard counts paid revenue minus refunds and the open orders`() {
        val before = mockMvc.perform(get("/v1/admin/dashboard").cookie(admin)).andExpect(status().isOk).json()
        val productId = stocked(quantity = 5, priceCents = 10000)["id"].asLong()
        val number = checkout(guestCart(productId)).andExpect(status().isCreated).json()["orderNumber"].asText()
        pay(number).andExpect(status().isOk)

        val after = mockMvc.perform(get("/v1/admin/dashboard").cookie(admin)).andExpect(status().isOk).json()

        assertEquals(before["today"]["revenueCents"].asLong() + 10000, after["today"]["revenueCents"].asLong())
        assertEquals(before["month"]["orders"].asLong() + 1, after["month"]["orders"].asLong())
        assertEquals(before["openOrders"].asLong() + 1, after["openOrders"].asLong())
        assertTrue(after["latestOrders"].any { it["number"].asText() == number })
    }

    @Test
    fun `cart quantities stay within the stock, and a sold-out product can still be lowered or removed`() {
        val productId = stocked(quantity = 2)["id"].asLong()
        val cartCookie = guestCart(productId, 2)

        val tooMany = addToCart(productId, 1, cartCookie).andExpect(status().isConflict).json()
        assertContains(tooMany["message"].asText(), "je hebt er al 2")
        mockMvc.perform(put("/v1/cart/items/$productId").cookie(cartCookie).json(mapOf("quantity" to 3))).andExpect(status().isConflict)

        adjustStock(productId, -2, "CORRECTION").andExpect(status().isOk)
        assertEquals("SOLD_OUT", cart(cartCookie)["items"].single()["problem"].asText())
        addToCart(productId, 1).andExpect(status().isConflict)
        mockMvc.perform(put("/v1/cart/items/$productId").cookie(cartCookie).json(mapOf("quantity" to 1))).andExpect(status().isOk)
        mockMvc.perform(delete("/v1/cart/items/$productId").cookie(cartCookie)).andExpect(status().isOk)
        assertEquals(0, cart(cartCookie)["itemCount"].asInt())
    }

    @Test
    fun `a product without a price can't go in the cart`() {
        val product = createProduct(priceCents = null)
        adjustStock(product["id"].asLong(), 3).andExpect(status().isOk)

        addToCart(product["id"].asLong()).andExpect(status().isConflict)
    }

    @Test
    fun `the admin changes shipping costs and the cart follows`() {
        val productId = stocked(quantity = 2, priceCents = 2000)["id"].asLong()
        val cartCookie = guestCart(productId)
        val rates = mockMvc.perform(get("/v1/admin/shipping-rates").cookie(admin)).andExpect(status().isOk).json()
        val nl = rates.first { it["country"].asText() == "NL" }
        try {
            mockMvc.perform(
                put("/v1/admin/shipping-rates/NL").cookie(admin)
                    .json(mapOf("priceCents" to 395, "freeFromCents" to 1500, "deliveryTime" to "Morgen in huis", "active" to true)),
            ).andExpect(status().isOk)

            val cart = cart(cartCookie)
            assertEquals(0, cart["shipping"]["priceCents"].asLong())
            assertEquals("Morgen in huis", cart["shipping"]["deliveryTime"].asText())
        } finally {
            mockMvc.perform(
                put("/v1/admin/shipping-rates/NL").cookie(admin).json(
                    mapOf(
                        "priceCents" to nl["priceCents"].asLong(),
                        "freeFromCents" to nl["freeFromCents"].asLong(),
                        "deliveryTime" to nl["deliveryTime"].asText(),
                        "active" to true,
                    ),
                ),
            ).andExpect(status().isOk)
        }
    }

    @Test
    fun `popular puts what sold first`() {
        val slow = stocked(name = "$word Aaa Tin", quantity = 5, type = "TIN")["id"].asLong()
        val seller = stocked(name = "$word Zzz Tin", quantity = 5, type = "TIN")["id"].asLong()
        assertEquals(listOf("$word Aaa Tin", "$word Zzz Tin"), names("sort=name"))

        val number = checkout(guestCart(seller)).andExpect(status().isCreated).json()["orderNumber"].asText()
        pay(number).andExpect(status().isOk)

        assertEquals("$word Zzz Tin", names("sort=popular").first())
        assertTrue(slow > 0)
    }

    @Test
    fun `admin endpoints for orders need an admin`() {
        val customer = loggedInUser(Role.CUSTOMER)
        mockMvc.perform(get("/v1/admin/orders/UT-AAAAAA").cookie(customer)).andExpect(status().isForbidden)
        mockMvc.perform(post("/v1/admin/orders/UT-AAAAAA/cancel").cookie(customer).json(emptyMap<String, Any>())).andExpect(status().isForbidden)
        mockMvc.perform(get("/v1/account/orders")).andExpect(status().isUnauthorized)
    }

    private fun expireReservation(number: String) {
        val order = order(number)
        order.reservedUntil = Instant.now().minusSeconds(60)
        orderRepository.save(order)
    }
}
