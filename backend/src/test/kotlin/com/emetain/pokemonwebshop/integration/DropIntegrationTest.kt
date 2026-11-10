package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.DropStatus
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.Role
import jakarta.mail.Message
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A drop from setting it up to the report: stock, the waiting room, buying, the end and the leftover. */
class DropIntegrationTest : DropTestSupport() {

    @Test
    fun `allocating moves stock out of the normal stock with a drop movement, and publishing shows the drop`() {
        val product = stocked(quantity = 10)
        val productId = product["id"].asLong()
        val drop = createDrop(listOf(productId to 6), publish = false)

        assertEquals(4, stock(productId)["onHand"].asInt(), "6 of 10 went to the drop")
        val movements = mockMvc.perform(get("/v1/admin/products/$productId/stock/movements").cookie(admin)).andExpect(status().isOk).json()
        val allocation = movements["items"].first()
        assertEquals("DROP_ALLOCATION", allocation["reason"].asText())
        assertEquals(-6, allocation["change"].asInt())

        mockMvc.perform(get("/v1/drops/${drop.slug}")).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/drops/${drop.slug}").cookie(admin)).andExpect(status().isOk)

        mockMvc.perform(post("/v1/admin/drops/${drop.id}/publish").cookie(admin)).andExpect(status().isOk)
        val list = mockMvc.perform(get("/v1/drops")).andExpect(status().isOk).json()
        assertTrue(list["upcoming"].any { it["slug"].asText() == drop.slug })
        val shown = detail(drop)
        assertEquals("SCHEDULED", shown["status"].asText())
        assertEquals(6, shown["products"][0]["available"].asInt())
        assertEquals(9995, shown["products"][0]["priceCents"].asLong())

        // Less goes back, never below what is sold or held.
        mockMvc.perform(put("/v1/admin/drops/${drop.id}/products/${drop.dropProductIds[0]}").cookie(admin).json(mapOf("allocated" to 2)))
            .andExpect(status().isOk)
        assertEquals(8, stock(productId)["onHand"].asInt())
        mockMvc.perform(put("/v1/admin/drops/${drop.id}/products/${drop.dropProductIds[0]}").cookie(admin).json(mapOf("allocated" to 50)))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `a drop can't be published without stock, a price or a start in the future`() {
        val unpriced = stocked(name = "$word Zonder Prijs", quantity = 3, priceCents = null)
        val drop = createDrop(listOf(unpriced["id"].asLong() to 2), publish = false)
        mockMvc.perform(post("/v1/admin/drops/${drop.id}/publish").cookie(admin)).andExpect(status().isBadRequest)

        // A drop price makes it sellable.
        mockMvc.perform(put("/v1/admin/drops/${drop.id}/products/${drop.dropProductIds[0]}").cookie(admin).json(mapOf("allocated" to 2, "priceCents" to 4995)))
            .andExpect(status().isOk)
        changeDrop(drop.id) { it.startsAt = Instant.now().minusSeconds(60) }
        mockMvc.perform(post("/v1/admin/drops/${drop.id}/publish").cookie(admin)).andExpect(status().isBadRequest)
        changeDrop(drop.id) { it.startsAt = Instant.now().plusSeconds(600) }
        mockMvc.perform(post("/v1/admin/drops/${drop.id}/publish").cookie(admin)).andExpect(status().isOk)
        assertEquals(4995, detail(drop)["products"][0]["priceCents"].asLong())
    }

    @Test
    fun `a draft can be deleted and its stock goes back`() {
        val product = stocked(quantity = 5)
        val drop = createDrop(listOf(product["id"].asLong() to 5), publish = false)
        assertEquals(0, stock(product["id"].asLong())["onHand"].asInt())

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/v1/admin/drops/${drop.id}").cookie(admin))
            .andExpect(status().isNoContent)
        assertEquals(5, stock(product["id"].asLong())["onHand"].asInt())
    }

    @Test
    fun `the drop goes live at its start time and everyone already waiting gets a random place, later arrivals the back`() {
        val product = stocked(quantity = 5)
        val drop = createDrop(listOf(product["id"].asLong() to 5))
        val early = (1..20).map { buyer() }
        for (buyer in early) {
            val joined = join(drop, buyer).andExpect(status().isOk).json()
            assertEquals("WAITING_ROOM", joined["state"].asText())
        }
        assertEquals(20, ping(drop, early[0])["waitingCount"].asLong())

        setSpeed(drop, 1)
        startNow(drop)
        assertEquals("LIVE", detail(drop)["status"].asText())

        val positions = transactions.execute {
            entryRepository.findAll().filter { it.drop.id == drop.id }.sortedBy { it.id }.map { it.position!! }
        }!!
        assertEquals((1L..20L).toSet(), positions.toSet(), "every early arrival has one place")
        assertNotEquals((1L..20L).toList(), positions, "the places are shuffled, not in the order people joined")

        val late = buyer()
        val lateStatus = join(drop, late).andExpect(status().isOk).json()
        assertEquals("QUEUED", lateStatus["state"].asText())
        val latePosition = transactions.execute { entryRepository.findAll().first { it.drop.id == drop.id && it.user?.email == late.email }.position }
        assertEquals(21L, latePosition, "after the start you join at the back")
    }

    @Test
    fun `the waiting room lets people through in batches at the set speed, and only those through get an access token`() {
        val product = stocked(quantity = 50)
        val drop = createDrop(listOf(product["id"].asLong() to 50))
        val buyers = (1..25).map { buyer() }
        buyers.forEach { join(drop, it).andExpect(status().isOk) }
        // 60 a minute in batches of 10 seconds: 10 per batch.
        setSpeed(drop, 60)
        startNow(drop)

        fun admitted() = buyers.count { ping(drop, it)["state"].asText() == "ADMITTED" }
        assertEquals(10, admitted(), "the first batch goes through at the start")
        assertTrue(buyers.filter { it.access != null }.size == 10)

        val waiting = buyers.filter { it.access == null }.map { ping(drop, it) }
        assertEquals((1L..15L).toList(), waiting.map { it["position"].asLong() }.sorted())
        assertTrue(waiting.all { it["estimatedSeconds"].asLong() >= 10 })

        assertEquals(0, dropJobs.advance(drop.id), "no new batch before the interval passed")
        assertEquals(10, nextBatch(drop))
        assertEquals(20, admitted())
        assertEquals(5, nextBatch(drop))
        assertEquals(25, admitted())
    }

    @Test
    fun `places of people who left are passed over, and a paused waiting room lets nobody through`() {
        val product = stocked(quantity = 50)
        val drop = createDrop(listOf(product["id"].asLong() to 50))
        val buyers = (1..6).map { buyer() }
        buyers.forEach { join(drop, it).andExpect(status().isOk) }
        setSpeed(drop, 12) // 2 per batch
        mockMvc.perform(post("/v1/admin/drops/${drop.id}/queue").cookie(admin).json(mapOf("paused" to true))).andExpect(status().isOk)
        startNow(drop)
        assertTrue(buyers.none { ping(drop, it)["state"].asText() == "ADMITTED" }, "paused: nobody through")
        assertTrue(ping(drop, buyers[0])["paused"].asBoolean())

        // Everyone but two left the page a while ago.
        val present = buyers.take(2)
        buyers.drop(2).forEach { gone -> changeEntry(gone, drop) { it.lastSeenAt = Instant.now().minus(Duration.ofMinutes(5)) } }
        mockMvc.perform(post("/v1/admin/drops/${drop.id}/queue").cookie(admin).json(mapOf("paused" to false))).andExpect(status().isOk)
        nextBatch(drop)
        present.forEach { assertEquals("ADMITTED", ping(drop, it)["state"].asText(), "the batch of 2 went to the people still there") }
    }

    @Test
    fun `someone whose time ran out gets back in line at the back`() {
        val product = stocked(quantity = 5)
        val drop = createDrop(listOf(product["id"].asLong() to 5))
        startNow(drop)
        val buyer = admittedBuyer(drop)
        changeEntry(buyer, drop) { it.accessExpiresAt = Instant.now().minusSeconds(1) }

        assertEquals("EXPIRED", ping(drop, buyer)["state"].asText())
        setQuantity(drop, buyer, drop.dropProductIds[0], 1).andExpect(status().isForbidden)

        setSpeed(drop, 1)
        val again = join(drop, buyer).andExpect(status().isOk).json()
        assertEquals("QUEUED", again["state"].asText())
        val entry = transactions.execute { entryRepository.findAll().first { it.drop.id == drop.id && it.user?.email == buyer.email } }!!
        assertEquals(2, entry.joinCount)
        assertNull(entry.accessExpiresAt)
    }

    @Test
    fun `checkout holds drop stock, the webhook sells it from the drop, and the normal stock stays the same`() {
        val product = stocked(quantity = 8)
        val productId = product["id"].asLong()
        val drop = createDrop(listOf(productId to 5), productMax = 2)
        startNow(drop)
        val buyer = admittedBuyer(drop)

        val cart = setQuantity(drop, buyer, drop.dropProductIds[0], 2).andExpect(status().isOk).json()
        assertEquals(2, cart["cart"]["itemCount"].asInt())
        assertEquals(19990, cart["cart"]["subtotalCents"].asLong())
        assertEquals(2, cart["limits"][0]["room"].asInt(), "the limit is 2, in the cart included")

        val started = checkout(drop, buyer).andExpect(status().isCreated).json()
        val number = started["orderNumber"].asText()
        assertEquals(2, dropProduct(drop.id, drop.dropProductIds[0]).reserved)
        assertEquals(3, detail(drop)["products"][0]["available"].asInt(), "nog 3 over")
        val request = gateway.sessions.getValue(FakePaymentGateway.sessionId(number))
        assertContains(request.cancelUrl, "drop=${drop.slug}")

        pay(number).andExpect(status().isOk)
        val order = orderRepository.findByNumber(number)!!
        assertEquals(OrderStatus.PAID, order.status)
        val sold = dropProduct(drop.id, drop.dropProductIds[0])
        assertEquals(2, sold.sold)
        assertEquals(0, sold.reserved)
        assertEquals(3, stock(productId)["onHand"].asInt(), "the sale came out of the drop, not the normal stock")
        assertEquals(0, ping(drop, buyer)["cartCount"].asInt(), "what was bought left the drop cart")
    }

    @Test
    fun `an unpaid drop checkout gives its stock back to the drop, and paying after that is refunded`() {
        val product = stocked(quantity = 3)
        val drop = createDrop(listOf(product["id"].asLong() to 3))
        startNow(drop)
        val buyer = admittedBuyer(drop)
        setQuantity(drop, buyer, drop.dropProductIds[0], 1).andExpect(status().isOk)
        val number = checkout(drop, buyer).andExpect(status().isCreated).json()["orderNumber"].asText()
        assertEquals(10, Duration.between(Instant.now(), orderRepository.findByNumber(number)!!.reservedUntil).toMinutes() + 1)

        transactions.executeWithoutResult { orderRepository.findByNumber(number)!!.reservedUntil = Instant.now().minusSeconds(60) }
        checkoutJobs.releaseExpiredReservations()
        assertEquals(0, dropProduct(drop.id, drop.dropProductIds[0]).reserved)
        assertEquals(3, detail(drop)["products"][0]["available"].asInt())

        pay(number).andExpect(status().isOk)
        val order = orderRepository.findByNumber(number)!!
        assertEquals(OrderStatus.CANCELLED, order.status)
        assertEquals(order.totalCents, order.refundedCents, "a late payment of a drop order always goes back")
        assertEquals(0, dropProduct(drop.id, drop.dropProductIds[0]).sold)
    }

    @Test
    fun `checking out again closes the earlier attempt, so the stock is held once`() {
        val product = stocked(quantity = 4)
        val drop = createDrop(listOf(product["id"].asLong() to 4))
        startNow(drop)
        val buyer = admittedBuyer(drop)
        setQuantity(drop, buyer, drop.dropProductIds[0], 2).andExpect(status().isOk)
        val first = checkout(drop, buyer).andExpect(status().isCreated).json()["orderNumber"].asText()
        val second = checkout(drop, buyer).andExpect(status().isCreated).json()["orderNumber"].asText()

        assertEquals(OrderStatus.CANCELLED, orderRepository.findByNumber(first)!!.status)
        assertEquals(OrderStatus.PENDING_PAYMENT, orderRepository.findByNumber(second)!!.status)
        assertEquals(2, dropProduct(drop.id, drop.dropProductIds[0]).reserved)
    }

    @Test
    fun `the drop ends at its end time and the unsold stock goes back to the normal stock`() {
        val product = stocked(quantity = 10)
        val productId = product["id"].asLong()
        val drop = createDrop(listOf(productId to 10))
        startNow(drop)
        val buyer = admittedBuyer(drop)
        buy(drop, buyer, drop.dropProductIds[0], 3)
        // One checkout still holds stock when the drop ends.
        val holder = admittedBuyer(drop)
        setQuantity(drop, holder, drop.dropProductIds[0], 2).andExpect(status().isOk)
        val pending = checkout(drop, holder).andExpect(status().isCreated).json()["orderNumber"].asText()

        changeDrop(drop.id) { it.endsAt = Instant.now().minusSeconds(1) }
        dropJobs.tick()
        val ended = live(drop.id)
        assertEquals("ENDED", ended["status"].asText())
        assertEquals("Eindtijd bereikt", ended["endReason"].asText())
        assertEquals(5, stock(productId)["onHand"].asInt(), "10 allocated, 3 sold, 2 held: 5 back")
        setQuantity(drop, holder, drop.dropProductIds[0], 1).andExpect(status().isConflict)

        // The held stock follows once that checkout runs out.
        transactions.executeWithoutResult { orderRepository.findByNumber(pending)!!.reservedUntil = Instant.now().minusSeconds(60) }
        checkoutJobs.releaseExpiredReservations()
        assertEquals(7, stock(productId)["onHand"].asInt())
        assertEquals(3, dropProduct(drop.id, drop.dropProductIds[0]).allocated)
        assertTrue(mockMvc.perform(get("/v1/drops")).andExpect(status().isOk).json()["past"].any { it["slug"].asText() == drop.slug })
    }

    @Test
    fun `a drop that keeps its leftover gives it back when the admin asks, and a drop ends by itself when everything is sold`() {
        val kept = stocked(name = "$word Bewaard", quantity = 4)
        val keepDrop = createDrop(listOf(kept["id"].asLong() to 4), extra = mapOf("leftover" to "KEEP"))
        startNow(keepDrop)
        mockMvc.perform(post("/v1/admin/drops/${keepDrop.id}/end").cookie(admin)).andExpect(status().isOk)
        assertEquals(0, stock(kept["id"].asLong())["onHand"].asInt(), "kept with the drop")
        mockMvc.perform(post("/v1/admin/drops/${keepDrop.id}/leftover").cookie(admin)).andExpect(status().isOk)
        assertEquals(4, stock(kept["id"].asLong())["onHand"].asInt())
        mockMvc.perform(post("/v1/admin/drops/${keepDrop.id}/leftover").cookie(admin)).andExpect(status().isBadRequest)

        val single = stocked(name = "$word Laatste", quantity = 1)
        val soldOutDrop = createDrop(listOf(single["id"].asLong() to 1))
        startNow(soldOutDrop)
        buy(soldOutDrop, admittedBuyer(soldOutDrop), soldOutDrop.dropProductIds[0])
        assertTrue(detail(soldOutDrop)["soldOut"].asBoolean())
        dropJobs.tick()
        val report = live(soldOutDrop.id)
        assertEquals("ENDED", report["status"].asText())
        assertEquals("Uitverkocht", report["endReason"].asText())
        assertNotNull(report["products"][0]["sellOutSeconds"].takeIf { !it.isNull })
    }

    @Test
    fun `the live view and report count the waiting room, sales, buyers and revenue`() {
        val first = stocked(name = "$word Eerste", quantity = 5)
        val second = stocked(name = "$word Tweede", quantity = 5)
        val drop = createDrop(listOf(first["id"].asLong() to 2, second["id"].asLong() to 5))
        val waiting = buyer()
        join(drop, waiting).andExpect(status().isOk)
        startNow(drop)
        val a = admittedBuyer(drop)
        val b = admittedBuyer(drop)
        buy(drop, a, drop.dropProductIds[0], 2)
        buy(drop, b, drop.dropProductIds[1], 1, extra = mapOf("address" to address + mapOf("houseNumber" to "14")))

        val report = live(drop.id)
        assertEquals(3, report["queue"]["joined"].asLong())
        assertEquals(1, report["queue"]["shuffled"].asInt())
        assertEquals(2, report["totals"]["orders"].asLong())
        assertEquals(2, report["totals"]["buyers"].asLong())
        assertEquals(3, report["totals"]["units"].asInt())
        assertEquals(3 * 9995L, report["totals"]["revenueCents"].asLong())
        val firstStats = report["products"].first { it["productId"].asLong() == first["id"].asLong() }
        assertEquals(0, firstStats["available"].asInt())
        assertTrue(firstStats["sellOutSeconds"].asLong() >= 0)
        assertEquals(firstStats["sellOutSeconds"].asLong(), report["totals"]["averageSellOutSeconds"].asLong())
    }

    @Test
    fun `herinner mij sends one mail shortly before the start`() {
        val product = stocked(quantity = 2)
        val drop = createDrop(listOf(product["id"].asLong() to 2), startsIn = Duration.ofHours(2))
        val email = "herinner-${word.lowercase()}@example.com"
        mockMvc.perform(post("/v1/drops/${drop.slug}/reminders").with(fromRandomIp()).json(mapOf("email" to email))).andExpect(status().isNoContent)
        mockMvc.perform(post("/v1/drops/${drop.slug}/reminders").with(fromRandomIp()).json(mapOf("email" to email.uppercase()))).andExpect(status().isNoContent)
        mockMvc.perform(post("/v1/drops/${drop.slug}/reminders").with(fromRandomIp()).json(mapOf("email" to "geen-adres"))).andExpect(status().isBadRequest)

        dropJobs.tick()
        assertTrue(mailSender.sent.none { it.getRecipients(Message.RecipientType.TO).first().toString() == email }, "not yet: two hours to go")

        changeDrop(drop.id) { it.startsAt = Instant.now().plus(Duration.ofMinutes(14)) }
        dropJobs.tick()
        dropJobs.tick()
        val mails = await().atMost(Duration.ofSeconds(5)).until(
            { mailSender.sent.filter { it.getRecipients(Message.RecipientType.TO).first().toString() == email } },
            { it.isNotEmpty() },
        )
        Thread.sleep(200)
        assertEquals(1, mailSender.sent.count { it.getRecipients(Message.RecipientType.TO).first().toString() == email })
        assertContains(mails.first().subject, "begint de drop")
        assertContains(plainText(mails.first())!!, "/drops/${drop.slug}")
        mockMvc.perform(post("/v1/drops/${drop.slug}/reminders").with(fromRandomIp()).json(mapOf("email" to "laat@example.com")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `upcoming and live drops get a hero slide by themselves, until they end or the admin switches it off`() {
        val product = stocked(quantity = 4)
        val drop = createDrop(listOf(product["id"].asLong() to 4), extra = mapOf("showInHero" to true, "description" to "Eerste regel.\nTweede regel."))
        try {
            fun dropSlide() = mockMvc.perform(get("/v1/shop/hero-slides")).andExpect(status().isOk).json()
                .firstOrNull { it["drop"] != null && !it["drop"].isNull && it["drop"]["slug"].asText() == drop.slug }

            val upcoming = assertNotNull(dropSlide())
            assertEquals("SCHEDULED", upcoming["drop"]["status"].asText())
            assertEquals("/drops/${drop.slug}", upcoming["linkUrl"].asText())
            assertEquals("Eerste regel.", upcoming["text"].asText())
            assertEquals("pokemon", upcoming["game"].asText())

            startNow(drop)
            assertEquals("LIVE", dropSlide()!!["drop"]["status"].asText())
            assertTrue(mockMvc.perform(get("/v1/drops/live")).andExpect(status().isOk).json().any { it["slug"].asText() == drop.slug })

            val current = adminDrop(drop.id)
            mockMvc.perform(
                put("/v1/admin/drops/${drop.id}").cookie(admin).json(
                    mapOf("name" to current["name"].asText(), "startsAt" to current["startsAt"].asText(), "showInHero" to false),
                ),
            ).andExpect(status().isOk)
            assertNull(dropSlide())
        } finally {
            changeDrop(drop.id) { it.showInHero = false }
            mockMvc.perform(post("/v1/admin/drops/${drop.id}/end").cookie(admin))
        }
        assertEquals(DropStatus.ENDED, dropRepository.findById(drop.id).orElseThrow().status)
    }

    @Test
    fun `a drop slide is black until the admin picks a colour, and shows the products until there is a banner`() {
        val withPicture = stocked(quantity = 2)
        val picture = uploadImage(withPicture["id"].asLong()).andExpect(status().isOk).json()["images"][0].asText()
        val withoutPicture = stocked(quantity = 2)
        val drop = createDrop(
            listOf(withoutPicture["id"].asLong() to 1, withPicture["id"].asLong() to 1),
            extra = mapOf("showInHero" to true),
        )
        try {
            fun dropSlide() = mockMvc.perform(get("/v1/shop/hero-slides")).andExpect(status().isOk).json()
                .first { it["drop"] != null && !it["drop"].isNull && it["drop"]["slug"].asText() == drop.slug }["drop"]
            val current = adminDrop(drop.id)
            fun save(vararg fields: Pair<String, Any>) = mockMvc.perform(
                put("/v1/admin/drops/${drop.id}").cookie(admin)
                    .json(mapOf("name" to current["name"].asText(), "startsAt" to current["startsAt"].asText(), "showInHero" to true) + fields),
            )

            assertEquals("BLACK", current["heroColour"].asText())
            assertEquals("BLACK", dropSlide()["colour"].asText())
            assertEquals(listOf(picture), dropSlide()["pictures"].map { it["url"].asText() })
            assertEquals(false, dropSlide()["pictures"][0]["card"].asBoolean())

            save("heroColour" to "YELLOW").andExpect(status().isOk)
            assertEquals("YELLOW", adminDrop(drop.id)["heroColour"].asText())
            assertEquals("YELLOW", dropSlide()["colour"].asText())
            save("heroColour" to "PURPLE").andExpect(status().isBadRequest)
            save().andExpect(status().isOk)
            assertEquals("YELLOW", dropSlide()["colour"].asText())

            mockMvc.perform(
                multipart("/v1/admin/drops/${drop.id}/banner").file(MockMultipartFile("file", "banner.png", "image/png", png)).cookie(admin),
            ).andExpect(status().isOk)
            assertTrue(dropSlide()["pictures"].isEmpty)
        } finally {
            changeDrop(drop.id) { it.showInHero = false }
        }
    }

    @Test
    fun `prices, limits and the start time are fixed once the drop is live`() {
        val product = stocked(quantity = 5)
        val drop = createDrop(listOf(product["id"].asLong() to 3), productMax = 1, maxPerCustomer = 2)
        startNow(drop)
        val current = adminDrop(drop.id)
        mockMvc.perform(
            put("/v1/admin/drops/${drop.id}").cookie(admin).json(
                mapOf("name" to "Nieuw", "startsAt" to current["startsAt"].asText(), "maxPerCustomer" to 5),
            ),
        ).andExpect(status().isBadRequest)
        mockMvc.perform(put("/v1/admin/drops/${drop.id}/products/${drop.dropProductIds[0]}").cookie(admin).json(mapOf("allocated" to 3, "maxPerCustomer" to 3)))
            .andExpect(status().isBadRequest)
        // More stock during the drop is fine.
        mockMvc.perform(put("/v1/admin/drops/${drop.id}/products/${drop.dropProductIds[0]}").cookie(admin).json(mapOf("allocated" to 5, "maxPerCustomer" to 1)))
            .andExpect(status().isOk)
        assertEquals(5, detail(drop)["products"][0]["allocated"].asInt())
    }

    @Test
    fun `only admins manage drops`() {
        val customer = loggedInUser(Role.CUSTOMER)
        mockMvc.perform(get("/v1/admin/drops").cookie(customer)).andExpect(status().isForbidden)
        mockMvc.perform(post("/v1/admin/drops").cookie(customer).json(mapOf("name" to "x", "startsAt" to Instant.now().toString())))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/v1/admin/drops")).andExpect(status().isUnauthorized)
    }
}
