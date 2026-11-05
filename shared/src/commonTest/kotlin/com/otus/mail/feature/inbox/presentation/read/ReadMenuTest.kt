package com.otus.mail.feature.inbox.presentation.read

import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.OwnAddresses
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.category.CategoryPlacement
import com.otus.mail.feature.inbox.domain.category.MailCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadMenuTest {
    private val alex = MailSender("Alex", "alex@example.org")
    private val grace = MailSender("Grace Hopper", "grace@example.org")
    private val ada = MailSender("Ada Lovelace", "ada@example.org")
    private val sentry = MailSender("Sentry", "news@sentry.io")
    private val own = OwnAddresses(mapOf("work" to listOf("alex@example.org")))

    private fun message(
        sender: MailSender = grace,
        to: List<MailSender> = listOf(alex, ada),
        flagged: Boolean = false,
    ) = MailMessage(
        "work/e1",
        sender,
        "Q4 launch plan",
        "",
        ReceivedAt("2026-10-12", "09:14"),
        MailLocation("Inbox", "Work", "work"),
        attributes = MailAttributes(flagged = flagged),
        to = to,
    )

    /** Each section as its caption, then each entry as its label and detail. */
    private fun menu(page: ReadPage): List<List<String>> =
        readMenu(page).map { section ->
            listOfNotNull(section.part.title()) + section.entries.map { entry -> entry.described() }
        }

    private fun ReadMenuEntry.described(): String = listOfNotNull(label(), detail()).joinToString(" | ")

    private val conversation =
        listOf("Whole conversation", "Move to… | Usually Projects / Q4", "Mute conversation", "Delete conversation")

    @Test
    fun newestThreadPage() =
        assertEquals(
            listOf(
                listOf(
                    "Reply to Grace",
                    "Forward",
                    "Mark as unread",
                    "Flag",
                    "View details | Headers, encryption, original",
                    "Print or save as PDF",
                ),
                listOf("Grace Hopper", "Add to contacts", "Block sender", "Report spam or phishing"),
                conversation,
            ),
            menu(ReadPage(message(), own, index = 0, count = 6, suggestedPath = listOf("Projects", "Q4"))),
        )

    @Test
    fun olderThreadPageOffersDeletingThisMessage() {
        val page = ReadPage(message(flagged = true), own, index = 4, count = 6, muted = true)
        val sections = readMenu(page)
        assertEquals(
            listOf(
                "Reply to Grace",
                "Forward",
                "Mark as unread",
                "Unflag",
                "View details",
                "Print or save as PDF",
                "Delete this message",
            ),
            sections.first().entries.map { it.label() },
        )
        assertEquals(
            "Only this one, not the thread",
            sections
                .first()
                .entries
                .last()
                .detail(),
        )
        assertEquals(ReadMenuEntry.Mute(on = true), sections.last().entries[1])
    }

    @Test
    fun singleMailMovesFromTheMessageSection() =
        assertEquals(
            listOf(
                listOf(
                    "Forward",
                    "Mark as unread",
                    "Flag",
                    "Move to…",
                    "View details | Headers, encryption, original",
                    "Print or save as PDF",
                ),
                listOf("Grace Hopper", "Add to contacts", "Block sender", "Report spam or phishing"),
            ),
            menu(ReadPage(message(to = listOf(alex)), own)),
        )

    @Test
    fun newsletterWithBlockedImages() =
        assertEquals(
            listOf(
                listOf(
                    "Always show images from Sentry",
                    "Forward",
                    "Mark as unread",
                    "Flag",
                    "Move to…",
                    "View details | Headers, encryption, original",
                    "Print or save as PDF",
                ),
                listOf("Sentry", "Add to contacts", "Block sender", "Report spam or phishing"),
            ),
            menu(ReadPage(message(sender = sentry), own, newsletter = true, imagesBlocked = true)),
        )

    @Test
    fun newsletterWithImagesShown() {
        val page = ReadPage(message(sender = sentry), own, newsletter = true, imagesBlocked = false)
        assertEquals(
            listOf("Forward", "Mark as unread", "Flag", "Move to…", "View details", "Print or save as PDF"),
            readMenu(page).first().entries.map { it.label() },
        )
    }

    @Test
    fun sentMessageHasNoReplyOrSenderSection() {
        val sent = message(sender = alex, to = listOf(grace, ada))
        val page = ReadPage(sent, own, index = 0, count = 2)
        assertEquals(
            listOf(
                listOf("Forward", "Mark as unread", "Flag", "View details", "Print or save as PDF"),
                listOf("Whole conversation", "Move to…", "Mute conversation", "Delete conversation"),
            ),
            readMenu(page).map { section ->
                listOfNotNull(section.part.title()) + section.entries.map { it.label() }
            },
        )
    }

    @Test
    fun categoryEntriesShowOnlyWhileCategoriesAreOn() {
        val sent = message(sender = alex, to = listOf(grace, ada))
        val promotions = CategoryPlacement(MailCategory.Promotions, emptyList())
        val labels = { page: ReadPage -> readMenu(page).map { section -> section.entries.map { it.label() } } }
        val on = labels(ReadPage(message(), own, category = promotions))
        assertEquals("Why is this in Promotions?", on.first()[on.first().indexOf("View details") + 1])
        assertEquals("Categorize sender\u2026", on[1].last())
        val off = labels(ReadPage(message(), own)).flatten()
        assertEquals(emptyList(), off.filter { "categor" in it.lowercase() })
        val ownMail = labels(ReadPage(sent, own, category = promotions))
        assertEquals(1, ownMail.size, "The user's own mail has no sender section to categorize")
    }

    @Test
    fun replyAllIsLabelledPlainly() =
        assertEquals("Reply all", ReadMenuEntry.OtherReply(MailAction.ReplyAll, "Grace").label())
}
