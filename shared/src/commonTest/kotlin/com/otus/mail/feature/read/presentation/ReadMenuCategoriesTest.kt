package com.otus.mail.feature.read.presentation

import com.otus.mail.core.categories.domain.CategoryPlacement
import com.otus.mail.core.categories.domain.MailCategory
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.OwnAddresses
import com.otus.mail.core.messages.domain.ReceivedAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The ⋯ menu offers the Inbox's category entries only while the Inbox sorts the conversation. */
class ReadMenuCategoriesTest {
    private val own = OwnAddresses(mapOf("ada" to listOf("me@example.org")))
    private val promotions = CategoryPlacement(MailCategory.Newsletters, emptyList())

    @Test
    fun aSortedPageSaysWhyAndLetsTheSenderBeCategorized() {
        val sections = readMenu(ReadPage(message("news@shop.example"), own, category = promotions)).sections
        val first = sections.first().entries
        val details = first.indexOf(ReadMenuEntry.ViewDetails)
        assertEquals(ReadMenuEntry.WhyCategory(MailCategory.Newsletters), first[details + 1])
        assertEquals(ReadMenuEntry.CategorizeSender, sections[1].entries.last())
    }

    @Test
    fun anUnsortedPageHasNoCategoryEntries() {
        val entries = readMenu(ReadPage(message("news@shop.example"), own)).sections.flatMap { it.entries }
        assertTrue(entries.none { it is ReadMenuEntry.WhyCategory || it == ReadMenuEntry.CategorizeSender })
    }

    @Test
    fun theUsersOwnMailHasNoSenderToCategorize() {
        val sections = readMenu(ReadPage(message("me@example.org"), own, category = promotions)).sections
        assertEquals(listOf(ReadMenuPart.ThisMessage), sections.map { it.part })
    }

    private fun message(sender: String) =
        MailMessage(
            id = "ada/e1",
            sender = MailSender("", sender),
            subject = "This week",
            body = "",
            received = ReceivedAt("2026-11-01", "09:00"),
            location = MailLocation("Inbox", "Ada", "ada"),
        )
}
