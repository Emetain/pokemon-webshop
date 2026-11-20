package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.PriceAlertKind
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils

/** The text of the day's alert mail: what went off, a link to each card and a link that switches each alert off. */
object PriceAlertMailText {

    data class Mail(val subject: String, val text: String, val html: String)

    fun compose(mail: AlertMail, baseUrl: String): Mail {
        val base = baseUrl.trimEnd('/')
        val subject = if (mail.lines.size == 1) {
            "Prijsalert: ${mail.lines.single().hit.alert.card.name}"
        } else {
            "${mail.lines.size} prijsalerts van vandaag"
        }
        val manage = "$base/prijsalerts"
        val textLines = mail.lines.joinToString("\n\n") { line ->
            "${title(line)}\n${what(line.hit)}\n${cardLink(line, base)}\nDeze alert uitzetten: ${offLink(line, base)}"
        }
        val text = """
            |Hoi ${mail.displayName},
            |
            |${if (mail.lines.size == 1) "Een van je prijsalerts ging vandaag af:" else "Deze prijsalerts gingen vandaag af:"}
            |
            |$textLines
            |
            |Al je prijsalerts: $manage
            |Je krijgt hooguit één mail per dag. De prijzen zijn trendprijzen van vannacht.
        """.trimMargin()
        val htmlLines = mail.lines.joinToString("") { line ->
            "<li style=\"margin-bottom:12px\"><a href=\"${cardLink(line, base)}\"><strong>${HtmlUtils.htmlEscape(title(line))}</strong></a><br>" +
                "${HtmlUtils.htmlEscape(what(line.hit))}<br>" +
                "<a href=\"${offLink(line, base)}\" style=\"color:#4a5f85;font-size:13px\">Deze alert uitzetten</a></li>"
        }
        val html = """
            <p>Hoi ${HtmlUtils.htmlEscape(mail.displayName)},</p>
            <p>${if (mail.lines.size == 1) "Een van je prijsalerts ging vandaag af:" else "Deze prijsalerts gingen vandaag af:"}</p>
            <ul style="padding-left:18px">$htmlLines</ul>
            <p><a href="$manage">Al je prijsalerts</a></p>
            <p style="color:#4a5f85;font-size:13px">Je krijgt hooguit één mail per dag. De prijzen zijn trendprijzen van vannacht.</p>
        """.trimIndent()
        return Mail(subject, text, html)
    }

    private fun title(line: AlertMailLine): String {
        val card = line.hit.alert.card
        return "${card.name} (${card.set.name}, ${card.number}, ${CardLabels.variant(line.hit.alert.variant)})"
    }

    private fun what(hit: AlertHit): String {
        val now = CardLabels.money(hit.nowCents, hit.currency)
        val compared = CardLabels.money(hit.comparedCents, hit.currency)
        return when (hit.alert.kind) {
            PriceAlertKind.BELOW -> "Nu $now: onder je grens van $compared."
            PriceAlertKind.CHANGE -> {
                val percent = hit.percent ?: 0
                val direction = if (percent >= 0) "gestegen" else "gedaald"
                "${kotlin.math.abs(percent)}% $direction: van $compared naar $now."
            }
        }
    }

    private fun cardLink(line: AlertMailLine, base: String): String {
        val card = line.hit.alert.card
        return "$base/kaarten/${card.game.slug}/${card.set.code}/${card.id}"
    }

    private fun offLink(line: AlertMailLine, base: String) = "$base/prijsalerts/uit?token=${line.offToken}"
}

/** Checks the price alerts once a day, after the card sync and the value snapshot, and mails what went off. */
@Component
class PriceAlertJob(
    private val alertService: PriceAlertService,
    private val mailService: MailService,
    private val properties: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.collection.price-alert-cron}", zone = "Europe/Amsterdam")
    fun run() {
        var mails = 0
        var failed = 0
        for (userId in alertService.userIdsWithAlerts()) {
            try {
                val mail = alertService.check(userId) ?: continue
                val composed = PriceAlertMailText.compose(mail, properties.baseUrl)
                mailService.sendMessage(mail.email, composed.subject, composed.text, composed.html)
                mails++
            } catch (exception: Exception) {
                // One customer failing doesn't stop the others.
                failed++
                log.warn("Price alerts of user {} not checked: {}", userId, exception.message)
            }
        }
        log.info("Price alerts checked: {} mails, {} failed", mails, failed)
    }
}
