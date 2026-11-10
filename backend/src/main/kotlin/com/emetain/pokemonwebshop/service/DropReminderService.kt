package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.domain.Drop
import com.emetain.pokemonwebshop.domain.DropReminder
import com.emetain.pokemonwebshop.domain.DropStatus
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.DropReminderRepository
import com.emetain.pokemonwebshop.repository.DropRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.HtmlUtils
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Herinner mij": one mail shortly before a drop starts. It comes before the start on purpose: everyone in the
 * waiting room at the start gets a random place, so a mail at the start itself would come too late to be fair.
 */
@Service
class DropReminderService(
    private val dropRepository: DropRepository,
    private val dropLocks: DropLocks,
    private val reminderRepository: DropReminderRepository,
    private val mailService: MailService,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Signing up twice is fine. A logged-in customer gets the mail on the account's address. */
    @Transactional
    fun signUp(dropId: Long, viewer: User?, email: String?) {
        val drop = dropRepository.findById(dropId).orElse(null)?.takeIf { it.status.public }
            ?: throw ApiException.notFound("We konden deze drop niet vinden.")
        if (drop.status != DropStatus.SCHEDULED) throw ApiException.validation("Deze drop is al begonnen.")
        if (drop.remindersSentAt != null) throw ApiException.validation("De drop begint zo. Ga meteen naar de wachtkamer.")
        val address = (viewer?.email ?: email)?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (!EMAIL.matches(address) || address.length > MAX_EMAIL_LENGTH) throw ApiException.validation("Vul een geldig e-mailadres in.")
        if (reminderRepository.findByDropIdAndEmail(drop.id, address) == null) {
            reminderRepository.saveAndFlush(DropReminder(drop, address, clock.instant()))
        }
    }

    /** For the scheduler. Each signup gets one mail, also when two backends run at once. Returns how many went out. */
    @Transactional
    fun send(dropId: Long): Int {
        val drop = dropLocks.lock(dropId) ?: return 0
        if (drop.status != DropStatus.SCHEDULED || drop.remindersSentAt != null) return 0
        val now = clock.instant()
        drop.remindersSentAt = now
        val reminders = reminderRepository.findUnsent(drop.id)
        val mail = DropMails.reminder(drop, properties.baseUrl, now)
        for (reminder in reminders) {
            reminder.sentAt = now
            mailService.sendMessage(reminder.email, mail.subject, mail.text, mail.html)
        }
        if (reminders.isNotEmpty()) log.info("Sent {} reminders for drop {}", reminders.size, drop.slug)
        return reminders.size
    }

    private companion object {
        const val MAX_EMAIL_LENGTH = 254
        val EMAIL = Regex("[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}")
    }
}

data class DropMail(val subject: String, val text: String, val html: String)

/** The texts of drop mails. Pure, so they can be tested without a mail server. */
object DropMails {
    private val ZONE = ZoneId.of("Europe/Amsterdam")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("nl-NL"))
    private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("nl-NL"))

    fun reminder(drop: Drop, baseUrl: String, now: Instant): DropMail {
        val link = "$baseUrl/drops/${drop.slug}"
        val start = drop.startsAt.atZone(ZONE)
        val sameDay = now.atZone(ZONE).toLocalDate() == start.toLocalDate()
        val whenText = if (sameDay) "vandaag om ${TIME.format(start)}" else "${DAY.format(start)} om ${TIME.format(start)}"
        val minutes = maxOf(1, java.time.Duration.between(now, drop.startsAt).toMinutes())
        val account = if (drop.requiresAccount) "Je hebt een account nodig om mee te doen. Log alvast in, dan ben je er klaar voor." else null
        val text = listOfNotNull(
            "Hoi,",
            "Over $minutes minuten begint de drop ${drop.name} bij Poketopia, $whenText.",
            "Ga nu naar de wachtkamer:\n$link",
            "Iedereen die voor de start in de wachtkamer zit, krijgt bij de start een willekeurige plek in de rij. Wie later komt, sluit achteraan aan. Op tijd zijn is dus genoeg, op de seconde verversen helpt niet.",
            account,
            "Je krijgt deze mail één keer, omdat je om een herinnering vroeg.",
        ).joinToString("\n\n")
        val name = HtmlUtils.htmlEscape(drop.name)
        val html = listOfNotNull(
            "<p>Hoi,</p>",
            "<p>Over $minutes minuten begint de drop <strong>$name</strong> bij Poketopia, $whenText.</p>",
            "<p><a href=\"$link\">Ga naar de wachtkamer</a></p>",
            "<p>Iedereen die voor de start in de wachtkamer zit, krijgt bij de start een willekeurige plek in de rij. Wie later komt, sluit achteraan aan. Op tijd zijn is dus genoeg, op de seconde verversen helpt niet.</p>",
            account?.let { "<p>$it</p>" },
            "<p>Je krijgt deze mail één keer, omdat je om een herinnering vroeg.</p>",
        ).joinToString("\n")
        return DropMail("Over $minutes minuten begint de drop: ${drop.name}", text, html)
    }
}
