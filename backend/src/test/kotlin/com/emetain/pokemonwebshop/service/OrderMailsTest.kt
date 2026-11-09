package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Carrier
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderLine
import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.domain.ShipmentPreference
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderMailsTest {

    private val product = Product(
        game = Game.POKEMON,
        type = ProductType.ELITE_TRAINER_BOX,
        name = "ETB",
        nameSearch = "etb",
        slug = "etb",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun order(name: String = "Ash Ketchum", preference: ShipmentPreference = ShipmentPreference.TOGETHER) = Order(
        number = "UT-ABC234",
        user = null,
        email = "ash@example.com",
        customerName = name,
        street = "Dorpsstraat",
        houseNumber = "12a",
        postcode = "1421 AB",
        city = "Uithoorn",
        country = "NL",
        subtotalCents = 6490,
        shippingCents = 495,
        totalCents = 6985,
        vatCents = 1212,
        deliveryTime = "1 tot 2 werkdagen",
        shipmentPreference = preference,
        accessToken = "token",
        cartId = null,
        termsAcceptedAt = Instant.EPOCH,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    ).apply {
        lines += OrderLine(this, product, "Delta Reign Elite Trainer Box", "etb", ProductType.ELITE_TRAINER_BOX, null, false, 5995, 1, 5995, true, LocalDate.of(2026, 12, 5))
        lines += OrderLine(this, product, "Surging Sparks Booster", "booster", ProductType.BOOSTER_PACK, null, false, 495, 1, 495, false, null)
    }

    @Test
    fun `the confirmation lists the lines, the totals, the address and when the pre-order ships`() {
        val mail = OrderMails.build(OrderMail.CONFIRMATION, order(), "https://shop.test/bestelling/UT-ABC234?token=token")

        assertContains(mail.subject, "UT-ABC234")
        assertContains(mail.text, "Hoi Ash,")
        assertContains(mail.text, "1 × Delta Reign Elite Trainer Box (pre-order, vanaf 5 december 2026): € 59,95")
        assertContains(mail.text, "Totaal: € 69,85 (waarvan € 12,12 btw)")
        assertContains(mail.text, "We versturen alles samen zodra de pre-order binnen is (verwacht vanaf 5 december 2026).")
        assertContains(mail.text, "Dorpsstraat 12a\n1421 AB Uithoorn\nNederland")
        assertContains(mail.html, "https://shop.test/bestelling/UT-ABC234?token=token")
        assertContains(mail.html, "Inclusief € 12,12 btw")
    }

    @Test
    fun `sending what is in stock first says the pre-order follows`() {
        val mail = OrderMails.build(OrderMail.CONFIRMATION, order(preference = ShipmentPreference.IN_STOCK_FIRST), "https://shop.test")

        assertContains(mail.text, "We versturen wat op voorraad is meteen. De pre-order volgt apart")
    }

    @Test
    fun `a name from a guest can't put HTML in the mail`() {
        val mail = OrderMails.build(OrderMail.CONFIRMATION, order(name = "<script>alert(1)</script> Bob"), "https://shop.test")

        assertFalse(mail.html.contains("<script>"))
        assertTrue(mail.html.contains("&lt;script&gt;"))
    }

    @Test
    fun `the shipped mail has the code and the link`() {
        val order = order().apply {
            carrier = Carrier.POSTNL
            trackingCode = "3SABC123"
            trackingUrl = "https://jouw.postnl.nl/track-and-trace/3SABC123-NL-1421AB"
        }
        val mail = OrderMails.build(OrderMail.SHIPPED, order, "https://shop.test")

        assertContains(mail.text, "onderweg met PostNL")
        assertContains(mail.text, "3SABC123")
        assertContains(mail.html, "https://jouw.postnl.nl/track-and-trace/3SABC123-NL-1421AB")
    }

    @Test
    fun `the cancel mail says how much goes back`() {
        val mail = OrderMails.build(OrderMail.CANCELLED, order().apply { paymentMethod = "ideal" }, "https://shop.test", 6985)

        assertContains(mail.text, "We hebben € 69,85 teruggestort op je bankrekening.")
    }
}
