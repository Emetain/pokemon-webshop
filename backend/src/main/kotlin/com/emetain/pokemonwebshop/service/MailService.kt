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

            Goed nieuws: $productName is weer op voorraad bij Uithoorn TCG.

            $link

            Op is op, dus wacht niet te lang. Je krijgt deze mail één keer. Wil je een volgende keer weer een bericht, meld je dan opnieuw aan op de productpagina.
        """.trimIndent()
        val name = HtmlUtils.htmlEscape(productName)
        val html = """
            <p>Hoi,</p>
            <p>Goed nieuws: <strong>$name</strong> is weer op voorraad bij Uithoorn TCG.</p>
            <p><a href="$link">Bekijk $name</a></p>
            <p>Op is op, dus wacht niet te lang. Je krijgt deze mail één keer. Wil je een volgende keer weer een bericht, meld je dan opnieuw aan op de productpagina.</p>
        """.trimIndent()

        send(to, "$productName is weer op voorraad", text, html)
    }

    private fun send(to: String, subject: String, text: String, html: String) {
        val message = mailSender.createMimeMessage()
        MimeMessageHelper(message, true, Charsets.UTF_8.name()).apply {
            setFrom(properties.mail.from)
            setTo(to)
            setSubject(subject)
            setText(text, html)
        }
        mailSender.send(message)
    }
}
