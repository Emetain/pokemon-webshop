package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.CardVariants
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProductIntegrationTest : ShopTestSupport() {

    @Test
    fun `the admin endpoints are for admins only`() {
        val customer = loggedInUser()
        mockMvc.perform(get("/v1/admin/products")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/v1/admin/products").cookie(customer)).andExpect(status().isForbidden)
        mockMvc.perform(post("/v1/admin/products").cookie(customer).json(mapOf("name" to "x"))).andExpect(status().isForbidden)
        mockMvc.perform(post("/v1/admin/products/1/stock/adjustments").cookie(customer).json(mapOf("change" to 1)))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/v1/admin/stock/low").cookie(customer)).andExpect(status().isForbidden)
        mockMvc.perform(get("/v1/admin/product-import/csv").cookie(customer)).andExpect(status().isForbidden)
        mockMvc.perform(get("/v1/admin/hero-slides").cookie(customer)).andExpect(status().isForbidden)
    }

    @Test
    fun `the admin can create every product type`() {
        val pikachu = uniqueCode()
        val setCode = importCards(listOf(providerCard(pikachu, name = "Pikachu", number = "25")))
        val cardId = card(pikachu).id
        val extras = mapOf(
            "SINGLE" to mapOf("cardId" to cardId, "condition" to "NEAR_MINT"),
            "GRADED_SLAB" to mapOf("cardId" to cardId, "gradingCompany" to "PSA", "grade" to "10"),
            "ACCESSORY" to mapOf("accessoryType" to "SLEEVES"),
        )
        val types = listOf(
            "BOOSTER_BOX", "ELITE_TRAINER_BOX", "BOOSTER_BUNDLE", "BOOSTER_PACK", "SLEEVED_BOOSTER", "BLISTER", "TIN",
            "COLLECTION_BOX", "STARTER_DECK", "SINGLE", "GRADED_SLAB", "BULK", "MYSTERY_BOX", "ACCESSORY",
        )
        for (type in types) {
            val product = createProduct(name = "$word $type", type = type, extra = extras[type].orEmpty() + ("setCode" to setCode))
            assertEquals(type, product["type"].asText())
            if (type == "SINGLE" || type == "GRADED_SLAB") assertEquals(cardId, product["card"]["id"].asLong())
        }
        assertEquals(types.size, shop("size=50")["results"]["totalItems"].asInt())
    }

    @Test
    fun `a single needs a card and a condition, a slab a grade, an accessory its kind`() {
        fun refused(type: String, extra: Map<String, Any?> = emptyMap()) = mockMvc.perform(
            post("/v1/admin/products").cookie(admin)
                .json(mapOf("game" to "pokemon", "type" to type, "name" to "$word x") + extra),
        ).andExpect(status().isBadRequest)

        refused("SINGLE")
        refused("GRADED_SLAB")
        refused("ACCESSORY")
        refused("BOOSTER_BOX", mapOf("priceCents" to 1000, "salePriceCents" to 1200))
        refused("BOOSTER_BOX", mapOf("preOrder" to true))
        refused("BOOSTER_BOX", mapOf("game" to null))
        refused("BOOSTER_BOX", mapOf("slug" to "Not A Slug"))
    }

    @Test
    fun `every stock change shows in the movement log, with who made it`() {
        val id = createProduct()["id"].asLong()

        adjustStock(id, 10, note = "Levering").andExpect(status().isOk)
            .andExpect(jsonPath("$.stock.onHand").value(10))
            .andExpect(jsonPath("$.movement.reason").value("PURCHASE"))
        adjustStock(id, -2, "CORRECTION", "Beschadigd").andExpect(status().isOk)
        adjustStock(id, 1, "RETURN").andExpect(status().isOk)
        mockMvc.perform(post("/v1/admin/products/$id/stock/count").cookie(admin).json(mapOf("onHand" to 4)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.stock.onHand").value(4))
            .andExpect(jsonPath("$.movement.change").value(-5))

        mockMvc.perform(get("/v1/admin/products/$id/stock/movements").cookie(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(4))
            .andExpect(jsonPath("$.items[0].reason").value("CORRECTION"))
            .andExpect(jsonPath("$.items[0].onHandAfter").value(4))
            .andExpect(jsonPath("$.items[3].reason").value("PURCHASE"))
            .andExpect(jsonPath("$.items[3].note").value("Levering"))
            .andExpect(jsonPath("$.items[3].adminName").value("Testgebruiker"))
    }

    @Test
    fun `stock can't go negative and a purchase can't be negative`() {
        val id = createProduct()["id"].asLong()
        adjustStock(id, 1).andExpect(status().isOk)

        adjustStock(id, -2, "CORRECTION").andExpect(status().isBadRequest)
        adjustStock(id, -1, "PURCHASE").andExpect(status().isBadRequest)
        adjustStock(id, 0, "CORRECTION").andExpect(status().isBadRequest)
        adjustStock(id, 1, "RESERVATION").andExpect(status().isBadRequest)
        mockMvc.perform(get("/v1/admin/products/$id").cookie(admin)).andExpect(jsonPath("$.stock.onHand").value(1))
    }

    @Test
    fun `a product without stock is sold out in lists and on its page, but stays in the shop`() {
        val product = createProduct()
        val slug = product["slug"].asText()

        val listed = shop("")["results"]["items"].single()
        assertFalse(listed["canBuy"].asBoolean())
        mockMvc.perform(get("/v1/products/$slug"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.product.canBuy").value(false))

        adjustStock(product["id"].asLong(), 3).andExpect(status().isOk)
        mockMvc.perform(get("/v1/products/$slug"))
            .andExpect(jsonPath("$.product.canBuy").value(true))
            .andExpect(jsonPath("$.product.lastFew").doesNotExist())
    }

    @Test
    fun `a product without a price can't be bought, even with stock`() {
        val id = createProduct(priceCents = null)["id"].asLong()
        adjustStock(id, 3).andExpect(status().isOk)

        assertFalse(shop("")["results"]["items"].single()["canBuy"].asBoolean())
        assertEquals(0, shop("inStock=true")["results"]["totalItems"].asInt())
    }

    @Test
    fun `drafts and archived products are not in the shop`() {
        val draft = createProduct(name = "$word Draft", status = "DRAFT")
        val active = createProduct(name = "$word Active")
        mockMvc.perform(post("/v1/admin/products/${active["id"]}/archive").cookie(admin))
            .andExpect(jsonPath("$.status").value("ARCHIVED"))

        assertEquals(0, shop("")["results"]["totalItems"].asInt())
        mockMvc.perform(get("/v1/products/${draft["slug"].asText()}")).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/products/${draft["slug"].asText()}").cookie(loggedInUser())).andExpect(status().isNotFound)
        mockMvc.perform(get("/v1/products/${draft["slug"].asText()}").cookie(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DRAFT"))
    }

    @Test
    fun `filters give the right products`() {
        stocked("$word Pokemon box", extra = mapOf("language" to "JP"))
        createProduct(name = "$word Pokemon sold out", type = "ELITE_TRAINER_BOX", priceCents = 5000)
        stocked("$word Pokemon sale", extra = mapOf("salePriceCents" to 7000), priceCents = 8000, type = "BOOSTER_BUNDLE")
        createProduct(
            name = "$word Pokemon pre-order",
            type = "ELITE_TRAINER_BOX",
            priceCents = 6000,
            extra = mapOf("preOrder" to true, "releaseDate" to LocalDate.now().plusDays(20).toString()),
        )
        createProduct(name = "$word Lorcana box", game = "lorcana", priceCents = 12000)
        createProduct(name = "$word Sleeves", game = null, type = "ACCESSORY", priceCents = 999, extra = mapOf("accessoryType" to "SLEEVES"))

        assertEquals(4, names("game=pokemon").size)
        assertEquals(listOf("$word Lorcana box"), names("game=lorcana"))
        assertEquals(setOf("$word Pokemon sold out", "$word Pokemon pre-order"), names("type=ELITE_TRAINER_BOX").toSet())
        assertEquals(listOf("$word Sleeves"), names("accessory=SLEEVES"))
        assertEquals(listOf("$word Pokemon box"), names("language=JP"))
        assertEquals(setOf("$word Pokemon box", "$word Pokemon sale"), names("inStock=true").toSet())
        assertEquals(listOf("$word Pokemon sale"), names("sale=true"))
        assertEquals(listOf("$word Pokemon pre-order"), names("preOrder=true"))
        // The sale product costs 70 euros with its sale price, so it is in this range although its price is 80.
        assertEquals(setOf("$word Pokemon sold out", "$word Pokemon pre-order", "$word Pokemon sale"), names("minPrice=5000&maxPrice=7000").toSet())
        mockMvc.perform(get("/v1/products?type=NOPE")).andExpect(status().isBadRequest)
    }

    @Test
    fun `sorting gives the right order, with stock first by default`() {
        createProduct(name = "$word A sold out", priceCents = 3000)
        stocked("$word B in stock", priceCents = 2000)
        createProduct(name = "$word C no price", priceCents = null)
        stocked("$word D on sale", priceCents = 5000, extra = mapOf("salePriceCents" to 1000))

        val featured = names("")
        assertEquals(setOf("$word B in stock", "$word D on sale"), featured.take(2).toSet())
        assertEquals(listOf("$word D on sale", "$word B in stock", "$word A sold out", "$word C no price"), names("sort=price-low"))
        assertEquals(listOf("$word A sold out", "$word B in stock", "$word D on sale", "$word C no price"), names("sort=price-high"))
        assertEquals(listOf("$word A sold out", "$word B in stock", "$word C no price", "$word D on sale"), names("sort=name"))
        assertEquals(listOf("$word D on sale", "$word C no price", "$word B in stock", "$word A sold out"), names("sort=new"))
    }

    @Test
    fun `popular means wanted until there are orders`() {
        val wanted = createProduct(name = "$word Wanted")
        createProduct(name = "$word Ignored")
        mockMvc.perform(put("/v1/wishlist/products/${wanted["id"]}").cookie(loggedInUser())).andExpect(status().isNoContent)
        mockMvc.perform(put("/v1/wishlist/products/${wanted["id"]}").cookie(loggedInUser())).andExpect(status().isNoContent)

        assertEquals("$word Wanted", names("sort=popular").first())
    }

    @Test
    fun `the filter options show what exists in that part of the shop`() {
        createProduct(name = "$word One Piece", game = "one-piece", type = "STARTER_DECK", extra = mapOf("language" to "JP"))

        val facets = mockMvc.perform(get("/v1/products?game=one-piece")).andExpect(status().isOk).json()["facets"]
        assertTrue("STARTER_DECK" in facets["types"].map { it.asText() })
        assertTrue("JP" in facets["languages"].map { it.asText() })
    }

    @Test
    fun `a product page shows the card and its market price for a single, and other products of the set`() {
        val charizard = uniqueCode()
        val setCode = importCards(
            listOf(
                providerCard(
                    charizard,
                    name = "Charizard",
                    number = "4",
                    variants = setOf(CardVariants.HOLO),
                    prices = listOf(providerPrice(CardVariants.HOLO, trend = 30000)),
                ),
            ),
        )
        val card = card(charizard)
        val single = stocked(
            "$word Charizard",
            type = "SINGLE",
            extra = mapOf("cardId" to card.id, "condition" to "EXCELLENT", "cardVariant" to CardVariants.HOLO),
        )
        stocked("$word Booster", type = "BOOSTER_PACK", extra = mapOf("setCode" to setCode))

        mockMvc.perform(get("/v1/products/${single["slug"].asText()}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.card.name").value("Charizard"))
            .andExpect(jsonPath("$.card.prices[0].trendCents").value(30000))
            .andExpect(jsonPath("$.card.prices[0].source").value("CARDMARKET"))
            .andExpect(jsonPath("$.cardVariant").value(CardVariants.HOLO))
            .andExpect(jsonPath("$.product.imageIsCard").value(true))
            .andExpect(jsonPath("$.related[0].name").value("$word Booster"))
    }

    @Test
    fun `set pages show which cards the shop has as a single, cheapest first`() {
        val mewId = uniqueCode()
        val setCode = importCards(listOf(providerCard(mewId, name = "Mew", number = "151"), providerCard(name = "Ditto", number = "132")))
        val mew = card(mewId)
        stocked("$word Mew NM", type = "SINGLE", priceCents = 900, extra = mapOf("cardId" to mew.id, "condition" to "NEAR_MINT"))
        stocked("$word Mew PL", type = "SINGLE", priceCents = 400, extra = mapOf("cardId" to mew.id, "condition" to "PLAYED"))
        createProduct(name = "$word Mew sold out", type = "SINGLE", priceCents = 100, extra = mapOf("cardId" to mew.id, "condition" to "POOR"))

        val cards = mockMvc.perform(get("/v1/catalog/pokemon/sets/$setCode/cards")).andExpect(status().isOk).json()["items"]
        val mewTile = cards.single { it["name"].asText() == "Mew" }
        assertEquals(400, mewTile["forSale"]["shownPriceCents"].asInt())
        assertEquals(2, mewTile["forSale"]["offers"].asInt())
        assertTrue(cards.single { it["name"].asText() == "Ditto" }["forSale"].isNull)

        mockMvc.perform(get("/v1/catalog/cards/${mew.id}"))
            .andExpect(jsonPath("$.offers", hasSize<Any>(2)))
            .andExpect(jsonPath("$.offers[0].name").value("$word Mew PL"))
    }

    @Test
    fun `the quick single suggests the market price in euros and never uses it as the price`() {
        val lugia = uniqueCode()
        val setCode = importCards(listOf(providerCard(lugia, name = "Lugia", number = "9", prices = listOf(providerPrice(trend = 2500)))))
        val card = card(lugia)

        mockMvc.perform(get("/v1/admin/products/singles/market/${card.id}").cookie(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.prices[0].trendEuroCents").value(2500))
            .andExpect(jsonPath("$.existing", hasSize<Any>(0)))

        val created = mockMvc.perform(
            post("/v1/admin/products/singles").cookie(admin)
                .json(mapOf("cardId" to card.id, "condition" to "NEAR_MINT", "priceCents" to null, "quantity" to 2)),
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.name", containsString("Lugia (Set $setCode 9)")))
            .andExpect(jsonPath("$.priceCents").doesNotExist())
            .andExpect(jsonPath("$.stock.onHand").value(2))
            .json()

        mockMvc.perform(get("/v1/admin/products/${created["id"]}/stock/movements").cookie(admin))
            .andExpect(jsonPath("$.items[0].reason").value("PURCHASE"))
        mockMvc.perform(get("/v1/admin/products/singles/market/${card.id}").cookie(admin))
            .andExpect(jsonPath("$.existing", hasSize<Any>(1)))
    }

    @Test
    fun `images can be uploaded, reordered and removed`() {
        val id = createProduct()["id"].asLong()
        val first = uploadImage(id).andExpect(status().isOk).json()["images"][0].asText()
        val second = uploadImage(id).andExpect(status().isOk).json()["images"][1].asText()

        mockMvc.perform(put("/v1/admin/products/$id/images").cookie(admin).json(mapOf("urls" to listOf(second, first))))
            .andExpect(jsonPath("$.images[0]").value(second))
        mockMvc.perform(get(first.removePrefix("/api"))).andExpect(status().isOk)

        mockMvc.perform(delete("/v1/admin/products/$id/images/1").cookie(admin))
            .andExpect(jsonPath("$.images", hasSize<Any>(1)))
        mockMvc.perform(get(first.removePrefix("/api"))).andExpect(status().isNotFound)

        uploadImage(id, "not an image".toByteArray()).andExpect(status().isBadRequest)
        mockMvc.perform(put("/v1/admin/products/$id/images").cookie(admin).json(mapOf("urls" to listOf(first))))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `a duplicate is a draft without stock that shares the images, and removing one keeps the other's file`() {
        val id = createProduct()["id"].asLong()
        val image = uploadImage(id).andExpect(status().isOk).json()["images"][0].asText()
        adjustStock(id, 5).andExpect(status().isOk)

        val copy = mockMvc.perform(post("/v1/admin/products/$id/duplicate").cookie(admin)).andExpect(status().isCreated).json()
        assertEquals("DRAFT", copy["status"].asText())
        assertEquals(0, copy["stock"]["onHand"].asInt())
        assertNotEquals(copy["slug"].asText(), createProduct(name = "$word other")["slug"].asText())
        assertEquals(image, copy["images"][0].asText())

        mockMvc.perform(delete("/v1/admin/products/${copy["id"]}/images/0").cookie(admin)).andExpect(status().isOk)
        mockMvc.perform(get(image.removePrefix("/api"))).andExpect(status().isOk)
    }

    @Test
    fun `the wishlist holds products for logged-in customers`() {
        val customer = loggedInUser()
        val id = createProduct()["id"].asLong()
        mockMvc.perform(put("/v1/wishlist/products/$id")).andExpect(status().isUnauthorized)

        repeat(2) { mockMvc.perform(put("/v1/wishlist/products/$id").cookie(customer)).andExpect(status().isNoContent) }
        mockMvc.perform(get("/v1/wishlist/products").cookie(customer))
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].wished").value(true))
        assertTrue(shop("", customer)["results"]["items"][0]["wished"].asBoolean())
        assertTrue(shop("")["results"]["items"][0]["wished"].isNull)

        mockMvc.perform(delete("/v1/wishlist/products/$id").cookie(customer)).andExpect(status().isNoContent)
        mockMvc.perform(get("/v1/wishlist/products").cookie(customer)).andExpect(jsonPath("$.totalItems").value(0))
    }

    @Test
    fun `people waiting for stock get one mail when it is back`() {
        val product = createProduct()
        val slug = product["slug"].asText()
        val email = uniqueEmail()

        repeat(2) {
            mockMvc.perform(post("/v1/products/$slug/stock-alerts").with(fromRandomIp()).json(mapOf("email" to email)))
                .andExpect(status().isNoContent)
        }
        mockMvc.perform(post("/v1/products/$slug/stock-alerts").with(fromRandomIp()).json(mapOf("email" to "geen-adres")))
            .andExpect(status().isBadRequest)

        adjustStock(product["id"].asLong(), 2).andExpect(status().isOk)
        val mails = waitForMails(email)
        assertEquals(1, mails.size)
        assertTrue(mails.single().subject.contains("weer op voorraad"))
        assertTrue(plainText(mails.single())!!.contains("/product/$slug"))

        // In stock now, so signing up makes no sense.
        mockMvc.perform(post("/v1/products/$slug/stock-alerts").with(fromRandomIp()).json(mapOf("email" to email)))
            .andExpect(status().isBadRequest)
        adjustStock(product["id"].asLong(), 2).andExpect(status().isOk)
        Thread.sleep(300)
        assertEquals(1, mails(email).size)
    }

    @Test
    fun `waiting for a product without a price mails once it gets a price`() {
        val product = createProduct(priceCents = null)
        val email = uniqueEmail()
        mockMvc.perform(post("/v1/products/${product["slug"].asText()}/stock-alerts").with(fromRandomIp()).json(mapOf("email" to email)))
            .andExpect(status().isNoContent)
        adjustStock(product["id"].asLong(), 2).andExpect(status().isOk)
        Thread.sleep(300)
        assertEquals(0, mails(email).size)

        val request = objectMapper.convertValue(product, Map::class.java).filterKeys { it != "stock" } + ("priceCents" to 1500)
        mockMvc.perform(put("/v1/admin/products/${product["id"]}").cookie(admin).json(request)).andExpect(status().isOk)
        assertEquals(1, waitForMails(email).size)
    }

    @Test
    fun `new arrivals are products that just came into stock`() {
        val older = stocked("$word Older")
        stocked("$word Newer")
        adjustStock(older["id"].asLong(), -5, "CORRECTION").andExpect(status().isOk)
        adjustStock(older["id"].asLong(), 1).andExpect(status().isOk)

        val arrivals = mockMvc.perform(get("/v1/products/new-arrivals?limit=24")).andExpect(status().isOk).json()
            .map { it["name"].asText() }.filter { it.startsWith(word) }
        assertEquals(listOf("$word Older", "$word Newer"), arrivals)
    }

    @Test
    fun `the admin table filters on stock, source and missing prices`() {
        val low = stocked("$word Low", quantity = 1)
        createProduct(name = "$word Never stocked")
        createProduct(name = "$word No price", priceCents = null)
        stocked("$word Plenty", quantity = 50)

        fun adminNames(query: String) = mockMvc.perform(get("/v1/admin/products?q=$word&$query").cookie(admin))
            .andExpect(status().isOk).json()["items"].map { it["name"].asText() }.toSet()

        assertEquals(setOf("$word Low"), adminNames("stock=low"))
        assertEquals(setOf("$word Never stocked", "$word No price"), adminNames("stock=out"))
        assertEquals(setOf("$word No price"), adminNames("price=missing"))
        assertEquals(4, adminNames("source=manual").size)
        assertTrue(mockMvc.perform(get("/v1/admin/stock/low").cookie(admin)).json().any { it["id"] == low["id"] })
    }

    private fun mails(to: String) = mailSender.sent.filter { message -> message.allRecipients.any { it.toString() == to } }

    private fun waitForMails(to: String): List<jakarta.mail.internet.MimeMessage> {
        repeat(50) {
            mails(to).takeIf { it.isNotEmpty() }?.let { return it }
            Thread.sleep(100)
        }
        return mails(to)
    }
}
