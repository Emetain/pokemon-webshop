package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.ShipmentPreference
import com.emetain.pokemonwebshop.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.util.HtmlUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class OrderMail {
    /** The order is placed and paid. With iDEAL, Bancontact and card this also says the payment came in. */
    CONFIRMATION,

    /** For a payment that comes in after the confirmation, as with slower payment methods. */
    PAYMENT_RECEIVED,
    SHIPPED,
    CANCELLED,
    REFUNDED,
}

/** Published inside the transaction that changed the order. The mail goes once that change is saved. */
data class OrderMailEvent(val orderId: Long, val mail: OrderMail, val amountCents: Long? = null)

data class MailContent(val subject: String, val text: String, val html: String)

@Service
class OrderMailService(
    private val orderRepository: OrderRepository,
    private val mailService: MailService,
    private val properties: AppProperties,
) {

    /** After the commit, so nobody gets a mail about a payment or shipment that was rolled back. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun onOrderMail(event: OrderMailEvent) {
        val order = orderRepository.findById(event.orderId).orElse(null) ?: return
        val content = OrderMails.build(event.mail, order, link(order), event.amountCents)
        mailService.sendMessage(order.email, content.subject, content.text, content.html)
    }

    /** A customer opens the order in the account. A guest gets the link with the order's own key. */
    private fun link(order: Order): String = if (order.user != null) {
        "${properties.baseUrl}/account/bestellingen/${order.number}"
    } else {
        "${properties.baseUrl}/bestelling/${order.number}?token=${order.accessToken}"
    }
}

/** The text of the order mails, plain and HTML. Call inside a transaction: it reads the lines. */
object OrderMails {

    private val DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("nl-NL"))
    private const val BRAND = "#1a56db"
    private const val INK = "#0c1f4a"
    private const val SOFT = "#4a5f85"
    private const val LINE = "#d8e3f5"

    fun build(mail: OrderMail, order: Order, link: String, amountCents: Long? = null): MailContent = when (mail) {
        OrderMail.CONFIRMATION -> confirmation(order, link)
        OrderMail.PAYMENT_RECEIVED -> paymentReceived(order, link)
        OrderMail.SHIPPED -> shipped(order, link)
        OrderMail.CANCELLED -> cancelled(order, link, amountCents ?: 0)
        OrderMail.REFUNDED -> refunded(order, link, amountCents ?: 0)
    }

    private fun confirmation(order: Order, link: String): MailContent {
        val intro = "We hebben je betaling ontvangen en gaan je bestelling klaarmaken. Hieronder staat wat je hebt besteld."
        val shipping = shipmentText(order)
        val text = listOfNotNull(
            greeting(order),
            intro,
            shipping,
            linesText(order),
            totalsText(order),
            addressText(order),
            "Bekijk je bestelling: $link",
            SIGNATURE,
        ).joinToString("\n\n")
        val html = layout(
            "Bedankt voor je bestelling",
            listOfNotNull(
                p(greeting(order)),
                p(intro),
                shipping?.let(::p),
                linesHtml(order),
                totalsHtml(order),
                addressHtml(order),
                button("Bekijk je bestelling", link),
            ).joinToString(""),
            order,
        )
        return MailContent("Bedankt voor je bestelling ${order.number}", text, html)
    }

    private fun paymentReceived(order: Order, link: String): MailContent {
        val body = "We hebben je betaling van ${Money.format(order.totalCents)} ontvangen. We gaan je bestelling nu klaarmaken."
        return MailContent(
            "Betaling ontvangen voor ${order.number}",
            listOf(greeting(order), body, "Bekijk je bestelling: $link", SIGNATURE).joinToString("\n\n"),
            layout("Betaling ontvangen", p(greeting(order)) + p(body) + button("Bekijk je bestelling", link), order),
        )
    }

    private fun shipped(order: Order, link: String): MailContent {
        val carrier = order.carrier?.let(TrackingLinks::carrierLabel) ?: "de vervoerder"
        val body = "Goed nieuws: je bestelling is onderweg met $carrier."
        val code = "Je track & trace-code is ${order.trackingCode}."
        val followText = order.trackingUrl?.let { "Volg je pakket: $it" }
        val preOrderNote = if (order.hasPreOrders && order.shipmentPreference == ShipmentPreference.IN_STOCK_FIRST) {
            "Je pre-order sturen we apart, zodra hij binnen is."
        } else {
            null
        }
        val text = listOfNotNull(greeting(order), body, code, followText, preOrderNote, "Bekijk je bestelling: $link", SIGNATURE)
            .joinToString("\n\n")
        val html = layout(
            "Je bestelling is onderweg",
            listOfNotNull(
                p(greeting(order)),
                p(body),
                rawP("Je track &amp; trace-code is <strong>${esc(order.trackingCode.orEmpty())}</strong>."),
                order.trackingUrl?.let { button("Volg je pakket", it) },
                preOrderNote?.let(::p),
                rawP("<a href=\"${esc(link)}\" style=\"color:$BRAND\">Bekijk je bestelling</a>"),
            ).joinToString(""),
            order,
        )
        return MailContent("Je bestelling ${order.number} is onderweg", text, html)
    }

    private fun cancelled(order: Order, link: String, refundedCents: Long): MailContent {
        val body = "Je bestelling ${order.number} is geannuleerd."
        val refund = if (refundedCents > 0) refundText(order, refundedCents) else null
        val text = listOfNotNull(greeting(order), body, refund, "Vragen? Beantwoord deze mail, dan helpen we je verder.", SIGNATURE)
            .joinToString("\n\n")
        val html = layout(
            "Je bestelling is geannuleerd",
            listOfNotNull(p(greeting(order)), p(body), refund?.let(::p), p("Vragen? Beantwoord deze mail, dan helpen we je verder."))
                .joinToString("") + button("Bekijk je bestelling", link),
            order,
        )
        return MailContent("Je bestelling ${order.number} is geannuleerd", text, html)
    }

    private fun refunded(order: Order, link: String, refundedCents: Long): MailContent {
        val body = refundText(order, refundedCents)
        return MailContent(
            "We hebben ${Money.format(refundedCents)} teruggestort",
            listOf(greeting(order), body, "Bekijk je bestelling: $link", SIGNATURE).joinToString("\n\n"),
            layout("Geld teruggestort", p(greeting(order)) + p(body) + button("Bekijk je bestelling", link), order),
        )
    }

    private fun refundText(order: Order, cents: Long): String {
        val method = when (order.paymentMethod) {
            PaymentMethods.CARD -> "je creditcard"
            else -> "je bankrekening"
        }
        return "We hebben ${Money.format(cents)} teruggestort op $method. Afhankelijk van je bank staat het binnen 2 tot 10 werkdagen op je rekening."
    }

    private fun greeting(order: Order) = "Hoi ${order.customerName.substringBefore(' ').ifBlank { order.customerName }},"

    /** For an order with pre-orders: when it goes out. */
    private fun shipmentText(order: Order): String? {
        if (!order.hasPreOrders) return null
        val expected = order.lines.filter { it.preOrder }.mapNotNull { it.shipsFrom }.maxOrNull()
        val date = expected?.let { " (verwacht vanaf ${date(it)})" }.orEmpty()
        return when {
            !order.hasInStock -> "Je bestelling is een pre-order. We versturen hem zodra hij binnen is$date."
            order.shipmentPreference == ShipmentPreference.IN_STOCK_FIRST ->
                "We versturen wat op voorraad is meteen. De pre-order volgt apart zodra hij binnen is$date."
            else -> "We versturen alles samen zodra de pre-order binnen is$date."
        }
    }

    private fun linesText(order: Order) = order.lines.joinToString("\n") { line ->
        val preOrder = if (line.preOrder) " (pre-order${line.shipsFrom?.let { ", vanaf ${date(it)}" }.orEmpty()})" else ""
        "${line.quantity} × ${line.productName}$preOrder: ${Money.format(line.lineTotalCents)}"
    }

    private fun totalsText(order: Order) = listOf(
        "Subtotaal: ${Money.format(order.subtotalCents)}",
        "Verzending: ${if (order.shippingCents == 0L) "gratis" else Money.format(order.shippingCents)}",
        "Totaal: ${Money.format(order.totalCents)} (waarvan ${Money.format(order.vatCents)} btw)",
    ).joinToString("\n")

    private fun addressText(order: Order) =
        "Verzendadres:\n${order.customerName}\n${order.street} ${order.houseNumber}\n${order.postcode} ${order.city}\n${country(order.country)}"

    private fun linesHtml(order: Order): String {
        val rows = order.lines.joinToString("") { line ->
            val preOrder = if (line.preOrder) {
                "<br><span style=\"color:$SOFT;font-size:13px\">Pre-order${line.shipsFrom?.let { ", vanaf ${date(it)}" }.orEmpty()}</span>"
            } else {
                ""
            }
            "<tr><td style=\"padding:8px 0;border-bottom:1px solid $LINE\">${line.quantity} × ${esc(line.productName)}$preOrder</td>" +
                "<td style=\"padding:8px 0;border-bottom:1px solid $LINE;text-align:right;white-space:nowrap\">${Money.format(line.lineTotalCents)}</td></tr>"
        }
        return "<table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;margin:16px 0\">$rows</table>"
    }

    private fun totalsHtml(order: Order): String {
        fun row(label: String, value: String, strong: Boolean = false): String {
            val weight = if (strong) "font-weight:700;font-size:17px;" else ""
            return "<tr><td style=\"padding:3px 0;$weight\">$label</td><td style=\"padding:3px 0;text-align:right;$weight\">$value</td></tr>"
        }
        return "<table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;margin:0 0 16px\">" +
            row("Subtotaal", Money.format(order.subtotalCents)) +
            row("Verzending", if (order.shippingCents == 0L) "Gratis" else Money.format(order.shippingCents)) +
            row("Totaal", Money.format(order.totalCents), strong = true) +
            "<tr><td colspan=\"2\" style=\"color:$SOFT;font-size:13px\">Inclusief ${Money.format(order.vatCents)} btw</td></tr></table>"
    }

    private fun addressHtml(order: Order) =
        "<p style=\"margin:0 0 16px\"><strong>Verzendadres</strong><br>${esc(order.customerName)}<br>" +
            "${esc(order.street)} ${esc(order.houseNumber)}<br>${esc(order.postcode)} ${esc(order.city)}<br>${country(order.country)}</p>"

    /** A paragraph of plain text. Names and addresses come from customers, so this always escapes. */
    private fun p(text: String) = rawP(esc(text))

    private fun rawP(html: String) = "<p style=\"margin:0 0 16px\">$html</p>"

    private fun button(label: String, href: String) =
        "<p style=\"margin:24px 0\"><a href=\"${esc(href)}\" style=\"display:inline-block;padding:12px 22px;border-radius:8px;" +
            "background:$BRAND;color:#ffffff;font-weight:600;text-decoration:none\">${esc(label)}</a></p>"

    private fun layout(title: String, body: String, order: Order) = """
        <!doctype html>
        <html lang="nl"><body style="margin:0;padding:24px 12px;background:#eef4fe;font-family:Arial,Helvetica,sans-serif;color:$INK;font-size:15px;line-height:1.5">
        <table role="presentation" width="100%" style="max-width:560px;margin:0 auto;border-collapse:collapse;background:#ffffff;border-radius:12px">
        <tr><td style="padding:20px 24px;background:$BRAND;border-radius:12px 12px 0 0;color:#ffffff;font-weight:700;font-size:18px">Uithoorn TCG</td></tr>
        <tr><td style="padding:24px">
        <h1 style="margin:0 0 4px;font-size:22px">${esc(title)}</h1>
        <p style="margin:0 0 20px;color:$SOFT;font-size:13px">Bestelling ${esc(order.number)}</p>
        $body
        <p style="margin:24px 0 0;color:$SOFT;font-size:13px">Groeten,<br>Uithoorn TCG</p>
        </td></tr></table></body></html>
    """.trimIndent()

    private fun date(date: LocalDate) = DATE.format(date)

    private fun country(code: String) = when (code) {
        "NL" -> "Nederland"
        "BE" -> "België"
        else -> code
    }

    private fun esc(text: String): String = HtmlUtils.htmlEscape(text, Charsets.UTF_8.name())

    private const val SIGNATURE = "Groeten,\nUithoorn TCG"
}
