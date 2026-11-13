package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.util.HtmlUtils

@Service
class MailService(
    private val mailSender: JavaMailSender,
    private val properties: AppProperties,
) {

    /**
     * Runs in the background, so a forgot-password request takes equally long for known and unknown
     * email addresses. A mail server problem doesn't fail the request, Spring logs it instead.
     */
    @Async
    fun sendPasswordReset(to: String, displayName: String, token: String) {
        val link = "${properties.baseUrl}/wachtwoord-herstellen?token=$token"
        val text = """
            Hoi $displayName,

            Je hebt een nieuw wachtwoord aangevraagd. Open deze link om het in te stellen:

            $link

            De link werkt 1 uur en maar één keer.

            Heb je dit niet zelf aangevraagd? Dan hoef je niets te doen. Je wachtwoord blijft hetzelfde.
        """.trimIndent()
        val html = """
            <p>Hoi ${HtmlUtils.htmlEscape(displayName)},</p>
            <p>Je hebt een nieuw wachtwoord aangevraagd. Klik op de link om het in te stellen:</p>
            <p><a href="$link">Nieuw wachtwoord instellen</a></p>
            <p>De link werkt 1 uur en maar één keer.</p>
            <p>Heb je dit niet zelf aangevraagd? Dan hoef je niets te doen. Je wachtwoord blijft hetzelfde.</p>
        """.trimIndent()

        send(to, "Stel je nieuwe wachtwoord in", text, html)
    }

    /** Runs in the background, so a stock change never waits for the mail server. */
    @Async
    fun sendBackInStock(to: String, productName: String, slug: String) {
        val link = "${properties.baseUrl}/product/$slug"
        val text = """
            Hoi,

            Goed nieuws: $productName is weer op voorraad bij Poketopia.

            $link

            Op is op, dus wacht niet te lang. Je krijgt deze mail één keer. Wil je een volgende keer weer een bericht, meld je dan opnieuw aan op de productpagina.
        """.trimIndent()
        val name = HtmlUtils.htmlEscape(productName)
        val html = """
            <p>Hoi,</p>
            <p>Goed nieuws: <strong>$name</strong> is weer op voorraad bij Poketopia.</p>
            <p><a href="$link">Bekijk $name</a></p>
            <p>Op is op, dus wacht niet te lang. Je krijgt deze mail één keer. Wil je een volgende keer weer een bericht, meld je dan opnieuw aan op de productpagina.</p>
        """.trimIndent()

        send(to, "$productName is weer op voorraad", text, html)
    }

    /** Runs in the background, so a payment or an admin action never waits for the mail server. */
    @Async
    fun sendMessage(to: String, subject: String, text: String, html: String) = send(to, subject, text, html)

    /** The double opt-in of the newsletter. Runs in the background, so a known address takes equally long. */
    @Async
    fun sendNewsletterConfirmation(to: String, confirmToken: String, unsubscribeToken: String) {
        val confirm = "${properties.baseUrl}/nieuwsbrief/bevestigen?token=$confirmToken"
        val unsubscribe = "${properties.baseUrl}/nieuwsbrief/afmelden?token=$unsubscribeToken"
        val text = """
            Hoi,

            Leuk dat je de nieuwsbrief van Poketopia wilt ontvangen. Bevestig je aanmelding met deze link:

            $confirm

            De link werkt 7 dagen. Heb je je niet zelf aangemeld? Dan hoef je niets te doen: zonder bevestiging sturen we je niets.

            Later afmelden kan altijd: $unsubscribe
        """.trimIndent()
        val html = """
            <p>Hoi,</p>
            <p>Leuk dat je de nieuwsbrief van Poketopia wilt ontvangen. Bevestig je aanmelding met deze link:</p>
            <p><a href="$confirm">Ja, ik wil de nieuwsbrief</a></p>
            <p>De link werkt 7 dagen. Heb je je niet zelf aangemeld? Dan hoef je niets te doen: zonder bevestiging sturen we je niets.</p>
            <p style="color:#4a5f85;font-size:13px">Later afmelden kan altijd: <a href="$unsubscribe">afmelden</a>.</p>
        """.trimIndent()
        send(to, "Bevestig je aanmelding voor de nieuwsbrief", text, html)
    }

    /** Where customer service mail goes: the shop's own address, or the sender address when none is set. */
    val shopAddress: String get() = properties.contact.email.ifBlank { properties.mail.from }

    /**
     * A ticket mail, to the shop or to the customer. [replyTo] makes answering the mail reach the other side. The text
     * is shown as written: the HTML is the same text, escaped, with its line breaks and clickable links.
     */
    @Async
    fun sendSupportMail(to: String, subject: String, text: String, replyTo: String?) {
        val body = HtmlUtils.htmlEscape(text).replace(LINK) { "<a href=\"${it.value}\">${it.value}</a>" }
        send(to, subject, text, "<div style=\"font-family:inherit;white-space:pre-wrap\">$body</div>", replyTo)
    }

    private fun send(to: String, subject: String, text: String, html: String, replyTo: String? = null) {
        val message = mailSender.createMimeMessage()
        MimeMessageHelper(message, true, Charsets.UTF_8.name()).apply {
            setFrom(properties.mail.from)
            setTo(to)
            replyTo?.let(::setReplyTo)
            setSubject(subject)
            setText(text, html)
        }
        mailSender.send(message)
    }

    private companion object {
        val LINK = Regex("https?://[^\\s<\"]+")
    }
}
