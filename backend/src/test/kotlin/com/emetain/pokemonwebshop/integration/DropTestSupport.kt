package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Drop
import com.emetain.pokemonwebshop.domain.DropProduct
import com.emetain.pokemonwebshop.domain.DropQueueEntry
import com.emetain.pokemonwebshop.repository.DropQueueEntryRepository
import com.emetain.pokemonwebshop.repository.DropRepository
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.emetain.pokemonwebshop.service.CheckoutJobs
import com.emetain.pokemonwebshop.service.DropJobs
import com.fasterxml.jackson.databind.JsonNode
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Makes drops through the admin API, and moves their clock by changing their times in the database and running the
 * drop scheduler, as the integration tests run on the real clock.
 */
abstract class DropTestSupport : ShopTestSupport() {

    @Autowired
    protected lateinit var gateway: FakePaymentGateway

    @Autowired
    protected lateinit var dropRepository: DropRepository

    @Autowired
    protected lateinit var entryRepository: DropQueueEntryRepository

    @Autowired
    protected lateinit var orderRepository: OrderRepository

    @Autowired
    protected lateinit var dropJobs: DropJobs

    @Autowired
    protected lateinit var checkoutJobs: CheckoutJobs

    @Autowired
    protected lateinit var transactions: TransactionTemplate

    /** A drop of a test, with the ids of its products in the drop and in the shop, in the order they were added. */
    data class TestDrop(val id: Long, val slug: String, val dropProductIds: List<Long>, val productIds: List<Long>)

    /** A customer and, once through the waiting room, the access cookie of the drop. */
    data class Buyer(val session: Cookie, val email: String, var access: Cookie? = null)

    protected val address = mapOf(
        "street" to "Dorpsstraat",
        "houseNumber" to "12a",
        "postcode" to "1421ab",
        "city" to "Uithoorn",
        "country" to "NL",
    )

    private val counter = AtomicInteger()

    @BeforeEach
    fun resetGateway() = gateway.reset()

    protected fun createDrop(
        products: List<Pair<Long, Int>>,
        maxPerCustomer: Int? = null,
        productMax: Int? = null,
        startsIn: Duration = Duration.ofMinutes(10),
        extra: Map<String, Any?> = emptyMap(),
        publish: Boolean = true,
    ): TestDrop {
        val body = mapOf(
            "name" to "$word drop ${counter.incrementAndGet()}",
            "startsAt" to Instant.now().plus(startsIn).toString(),
            "maxPerCustomer" to maxPerCustomer,
            // Other tests compare the hero slides: a drop of a test only gets a slide when the test asks for one.
            "showInHero" to false,
        ) + extra
        val id = mockMvc.perform(post("/v1/admin/drops").cookie(admin).json(body)).andExpect(status().isCreated).json()["id"].asLong()
        for ((productId, allocated) in products) {
            addDropProduct(id, productId, allocated, productMax).andExpect(status().isCreated)
        }
        if (publish) mockMvc.perform(post("/v1/admin/drops/$id/publish").cookie(admin)).andExpect(status().isOk)
        val drop = adminDrop(id)
        return TestDrop(id, drop["slug"].asText(), drop["products"].map { it["id"].asLong() }, drop["products"].map { it["productId"].asLong() })
    }

    protected fun addDropProduct(dropId: Long, productId: Long, allocated: Int, max: Int? = null, priceCents: Long? = null): ResultActions =
        mockMvc.perform(
            post("/v1/admin/drops/$dropId/products").cookie(admin)
                .json(mapOf("productId" to productId, "allocated" to allocated, "maxPerCustomer" to max, "priceCents" to priceCents)),
        )

    protected fun adminDrop(id: Long): JsonNode =
        mockMvc.perform(get("/v1/admin/drops/$id").cookie(admin)).andExpect(status().isOk).json()

    protected fun live(id: Long): JsonNode =
        mockMvc.perform(get("/v1/admin/drops/$id/live").cookie(admin)).andExpect(status().isOk).json()

    protected fun detail(drop: TestDrop): JsonNode =
        mockMvc.perform(get("/v1/drops/${drop.slug}")).andExpect(status().isOk).json()

    protected fun changeDrop(id: Long, change: (Drop) -> Unit) =
        transactions.executeWithoutResult { change(dropRepository.findById(id).orElseThrow()) }

    protected fun dropProduct(id: Long, dropProductId: Long): DropProduct =
        transactions.execute { dropRepository.findById(id).orElseThrow().products.first { it.id == dropProductId }.also { it.product.name } }!!

    protected fun changeEntry(buyer: Buyer, drop: TestDrop, change: (DropQueueEntry) -> Unit) = transactions.executeWithoutResult {
        change(entryRepository.findAll().first { it.drop.id == drop.id && it.user?.email == buyer.email })
    }

    /** The start time came: the scheduler makes the drop live and shuffles the waiting room. */
    protected fun startNow(drop: TestDrop) {
        changeDrop(drop.id) { it.startsAt = Instant.now().minusMillis(500) }
        dropJobs.tick()
    }

    /** One batch interval passed: the scheduler lets the next batch through. */
    protected fun nextBatch(drop: TestDrop): Int {
        changeDrop(drop.id) { it.admittedUntil = Instant.now().minus(Duration.ofSeconds(10)) }
        return dropJobs.advance(drop.id)
    }

    protected fun setSpeed(drop: TestDrop, perMinute: Int) =
        mockMvc.perform(post("/v1/admin/drops/${drop.id}/queue").cookie(admin).json(mapOf("admitPerMinute" to perMinute)))
            .andExpect(status().isOk)

    protected fun MockHttpServletRequestBuilder.as_(buyer: Buyer?): MockHttpServletRequestBuilder {
        if (buyer == null) return this
        return if (buyer.access != null) cookie(buyer.session, buyer.access!!) else cookie(buyer.session)
    }

    protected fun buyer(): Buyer {
        val email = uniqueEmail()
        return Buyer(loggedInUser(email = email), email)
    }

    protected fun join(drop: TestDrop, buyer: Buyer?, body: Map<String, Any?> = emptyMap()): ResultActions =
        mockMvc.perform(post("/v1/drops/${drop.slug}/queue").with(fromRandomIp()).as_(buyer).json(body))

    /** Asks for the place in line, and keeps the access cookie when the shop hands one out. */
    protected fun ping(drop: TestDrop, buyer: Buyer): JsonNode {
        val result = mockMvc.perform(post("/v1/drops/${drop.slug}/queue/ping").with(fromRandomIp()).as_(buyer))
            .andExpect(status().isOk)
        result.andReturn().response.getCookie("drop_access")?.let { buyer.access = it }
        return result.json()
    }

    /** A customer in a live drop who got through and holds the access cookie. Lets everyone in line through. */
    protected fun admittedBuyer(drop: TestDrop): Buyer {
        val buyer = buyer()
        join(drop, buyer).andExpect(status().isOk)
        setSpeed(drop, 6000)
        nextBatch(drop)
        val status = ping(drop, buyer)
        check(status["state"].asText() == "ADMITTED") { "Not through: $status" }
        return buyer
    }

    protected fun setQuantity(drop: TestDrop, buyer: Buyer, dropProductId: Long, quantity: Int): ResultActions =
        mockMvc.perform(
            put("/v1/drops/${drop.slug}/cart/$dropProductId").with(fromRandomIp()).as_(buyer).json(mapOf("quantity" to quantity)),
        )

    protected fun checkout(drop: TestDrop, buyer: Buyer?, extra: Map<String, Any?> = emptyMap()): ResultActions {
        val body = mapOf("address" to address, "acceptTerms" to true) + extra
        return mockMvc.perform(post("/v1/drops/${drop.slug}/checkout").with(fromRandomIp()).as_(buyer).json(body))
    }

    protected fun webhook(type: String, sessionId: String): ResultActions {
        val (payload, signature) = FakePaymentGateway.signedEvent(type, sessionId)
        return mockMvc.perform(
            post("/v1/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(payload).header("Stripe-Signature", signature),
        )
    }

    protected fun pay(orderNumber: String): ResultActions {
        val sessionId = FakePaymentGateway.sessionId(orderNumber)
        gateway.paid += sessionId
        return webhook("checkout.session.completed", sessionId)
    }

    /** Puts [quantity] in the drop cart, checks out and pays. Returns the order number. */
    protected fun buy(drop: TestDrop, buyer: Buyer, dropProductId: Long, quantity: Int = 1, extra: Map<String, Any?> = emptyMap()): String {
        setQuantity(drop, buyer, dropProductId, quantity).andExpect(status().isOk)
        val number = checkout(drop, buyer, extra).andExpect(status().isCreated).json()["orderNumber"].asText()
        pay(number).andExpect(status().isOk)
        return number
    }

    protected fun stock(productId: Long): JsonNode =
        mockMvc.perform(get("/v1/admin/products/$productId").cookie(admin)).andExpect(status().isOk).json()["stock"]
}
