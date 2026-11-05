package com.otus.mail.feature.inbox.domain.category

import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InboxCategoriesTest {
    private val on = InboxCategories(enabled = true, hidden = emptySet())

    @Test
    fun theSendersChoiceComesBeforeTheClassifier() {
        val sale = message("1", "hello@brand.example", classify(SaleMail))
        assertEquals(MailCategory.Promotions, on.placement(sale).category)
        val chosen = on.withSender("Hello@Brand.example ", MailCategory.Primary)
        val placement = chosen.placement(sale)
        assertEquals(MailCategory.Primary, placement.category)
        assertEquals(listOf(CategoryReason(ReasonKind.SenderChoice, "hello@brand.example")), placement.reasons)
    }

    @Test
    fun theSendersChoiceOverridesStructuredDataToo() {
        val receipt = message("1", "orders@shop.example", classify(MarkedUpReceipt))
        val chosen = on.withSender("orders@shop.example", MailCategory.Updates)
        assertEquals(MailCategory.Updates, chosen.placement(receipt).category)
    }

    @Test
    fun clearingTheChoiceHandsTheSenderBack() {
        val chosen = on.withSender("a@b.example", MailCategory.Social).withSender("A@B.example", null)
        assertEquals(emptyMap(), chosen.senders)
        assertEquals(on, on.withSender("  ", MailCategory.Social), "An empty address changes nothing")
    }

    @Test
    fun aHiddenCategorysMailShowsInPrimaryAndSaysWhy() {
        val social = message("1", "notification@facebookmail.com", classify(SocialNotification))
        val placement = InboxCategories(enabled = true).placement(social)
        assertEquals(MailCategory.Primary, placement.category)
        assertEquals(
            listOf(
                CategoryReason(ReasonKind.HiddenCategory, "social"),
                CategoryReason(ReasonKind.KnownSender, "facebookmail.com"),
            ),
            placement.reasons,
        )
    }

    @Test
    fun timeSensitiveMailShowsInPrimaryAndItsOwnCategory() {
        val code = message("1", "no-reply@bank.example", classify(SecurityCode))
        val newsletter = message("2", "digest@weekly.example", classify(Newsletter))
        val mail = listOf(code, newsletter)
        assertEquals(listOf(code), on.inTab(mail, MailCategory.Primary))
        assertEquals(listOf(code), on.inTab(mail, MailCategory.Updates))
        assertEquals(listOf(newsletter), on.inTab(mail, MailCategory.Newsletters))
        val chosen = on.withSender("no-reply@bank.example", MailCategory.Promotions)
        assertEquals(listOf(code), chosen.inTab(mail, MailCategory.Primary), "A sender's choice keeps a code in view")
    }

    @Test
    fun mailWithoutAVerdictIsPrimary() {
        val plain = message("1", "ada@lovelace.example", verdict = null)
        assertEquals(MailCategory.Primary, on.placement(plain).category)
        assertEquals(emptyList(), on.placement(plain).reasons)
    }

    @Test
    fun unreadCountsCoverEachShownPill() {
        val mail =
            listOf(
                message("1", "a@x.example", classify(SaleMail), unread = true),
                message("2", "b@x.example", classify(SaleMail), unread = false),
                message("3", "c@x.example", classify(SecurityCode), unread = true),
                message("4", "d@x.example", classify(PersonalMail), unread = true),
                message("5", "e@x.example", classify(SocialNotification), unread = true),
            )
        val counts = InboxCategories(enabled = true).unreadCounts(mail)
        assertEquals(
            mapOf(
                MailCategory.Primary to 3,
                MailCategory.Transactions to 0,
                MailCategory.Updates to 1,
                MailCategory.Newsletters to 0,
                MailCategory.Promotions to 1,
            ),
            counts,
            "Social is hidden by default, so its mail counts in Primary",
        )
    }

    @Test
    fun primaryCannotBeHidden() {
        val categories =
            on
                .withShown(MailCategory.Primary, shown = false)
                .withShown(MailCategory.Updates, shown = false)
        assertEquals(MailCategory.entries - MailCategory.Updates, categories.tabs)
        assertTrue(categories.shows(MailCategory.Primary))
        assertEquals(MailCategory.entries, categories.withShown(MailCategory.Updates, shown = true).tabs)
    }

    @Test
    fun choicesAreSavedAndUnknownLinesSkipped() {
        val categories =
            InboxCategories(
                enabled = true,
                hidden = setOf(MailCategory.Social, MailCategory.Updates),
                senders = mapOf("a@b.example" to MailCategory.Promotions, "c@d.example" to MailCategory.Primary),
            )
        val hidden = InboxCategories.decodeHidden(InboxCategories.encodeHidden(categories))
        assertEquals(categories.hidden, hidden)
        assertEquals(categories.senders, InboxCategories.decodeSenders(InboxCategories.encodeSenders(categories)))
        assertEquals(null, InboxCategories.decodeHidden(null), "Nothing saved keeps the default")
        assertEquals(emptySet(), InboxCategories.decodeHidden(""), "Showing every category is a choice too")
        assertEquals(
            mapOf("x@y.example" to MailCategory.Social),
            InboxCategories.decodeSenders("x@y.example\tsocial\nbad\tforums\nnotab\n\tsocial"),
        )
    }

    private fun message(
        id: String,
        address: String,
        verdict: CategoryVerdict?,
        unread: Boolean = false,
    ) = MailMessage(
        id = id,
        sender = MailSender("", address),
        subject = "",
        body = "",
        received = ReceivedAt("2026-11-01", "09:00"),
        location = MailLocation("Inbox", "Work"),
        attributes = MailAttributes(unread = unread),
        category = verdict,
    )
}
