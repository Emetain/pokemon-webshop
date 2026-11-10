package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Drop
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DropMailsTest {

    private val now = Instant.parse("2026-11-14T18:45:00Z")

    private fun drop(requiresAccount: Boolean = true) = Drop(
        name = "Pikachu & <Friends>",
        slug = "pikachu-friends",
        startsAt = Instant.parse("2026-11-14T19:00:00Z"),
        createdAt = now,
        updatedAt = now,
        admitPerMinute = 60,
        requiresAccount = requiresAccount,
    )

    @Test
    fun `the reminder says when it starts in Dutch time, links to the drop and explains the random order`() {
        val mail = DropMails.reminder(drop(), "https://uithoorntcg.nl", now)
        assertEquals("Over 15 minuten begint de drop: Pikachu & <Friends>", mail.subject)
        assertContains(mail.text, "vandaag om 20:00")
        assertContains(mail.text, "https://uithoorntcg.nl/drops/pikachu-friends")
        assertContains(mail.text, "willekeurige plek")
        assertContains(mail.text, "account nodig")
        assertContains(mail.html, "Pikachu &amp; &lt;Friends&gt;")
        assertFalse(mail.html.contains("<Friends>"))
    }

    @Test
    fun `a drop without account doesn't ask to log in`() {
        assertFalse(DropMails.reminder(drop(requiresAccount = false), "https://uithoorntcg.nl", now).text.contains("account"))
    }
}
