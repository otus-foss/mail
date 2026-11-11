package com.otusfoss.mail.feature.inbox.presentation

import com.otusfoss.mail.core.categories.domain.CategoryReason
import com.otusfoss.mail.core.categories.domain.CategoryVerdict
import com.otusfoss.mail.core.categories.domain.InboxCategories
import com.otusfoss.mail.core.categories.domain.MailCategory
import com.otusfoss.mail.core.categories.domain.ReasonKind
import com.otusfoss.mail.core.messages.domain.MailAttributes
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.feature.inbox.domain.category.MarkedUpReceipt
import com.otusfoss.mail.feature.inbox.domain.category.Newsletter
import com.otusfoss.mail.feature.inbox.domain.category.PersonalMail
import com.otusfoss.mail.feature.inbox.domain.category.SaleMail
import com.otusfoss.mail.feature.inbox.domain.category.SecurityCode
import com.otusfoss.mail.feature.inbox.domain.category.SocialNotification
import com.otusfoss.mail.feature.inbox.domain.category.classify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InboxCategoriesTest {
    private val on = InboxCategories(enabled = true, hidden = emptySet())

    @Test
    fun theSendersChoiceComesBeforeTheClassifier() {
        val sale = message("1", "hello@brand.example", classify(SaleMail))
        assertEquals(MailCategory.Newsletters, on.placement(sale).category)
        val chosen = on.withSender("Hello@Brand.example ", MailCategory.Primary)
        val placement = chosen.placement(sale)
        assertEquals(MailCategory.Primary, placement.category)
        assertEquals(listOf(CategoryReason(ReasonKind.SenderChoice, "hello@brand.example")), placement.reasons)
    }

    @Test
    fun theSendersChoiceOverridesStructuredDataToo() {
        val receipt = message("1", "orders@shop.example", classify(MarkedUpReceipt))
        val chosen = on.withSender("orders@shop.example", MailCategory.Notifications)
        assertEquals(MailCategory.Notifications, chosen.placement(receipt).category)
    }

    @Test
    fun clearingTheChoiceHandsTheSenderBack() {
        val chosen = on.withSender("a@b.example", MailCategory.Newsletters).withSender("A@B.example", null)
        assertEquals(emptyMap(), chosen.senders)
        assertEquals(on, on.withSender("  ", MailCategory.Newsletters), "An empty address changes nothing")
    }

    @Test
    fun aHiddenCategorysMailShowsInPrimaryAndSaysWhy() {
        val social = message("1", "notification@facebookmail.com", classify(SocialNotification))
        val placement = InboxCategories(enabled = true, hidden = setOf(MailCategory.Notifications)).placement(social)
        assertEquals(MailCategory.Primary, placement.category)
        assertEquals(
            listOf(
                CategoryReason(ReasonKind.HiddenCategory, "notifications"),
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
        assertEquals(listOf(code), on.inTab(mail, MailCategory.Notifications))
        assertEquals(listOf(newsletter), on.inTab(mail, MailCategory.Newsletters))
        val chosen = on.withSender("no-reply@bank.example", MailCategory.Newsletters)
        assertEquals(listOf(code), chosen.inTab(mail, MailCategory.Primary), "A sender's choice keeps a code in view")
    }

    @Test
    fun pressingTheSelectedPillGoesBackToPrimary() {
        var picked: MailCategory? = null
        val tabs = CategoryTabs(on, MailCategory.Newsletters, emptyMap()) { picked = it }
        tabs.press(MailCategory.Newsletters)
        assertEquals(MailCategory.Primary, picked)
        tabs.press(MailCategory.Transactions)
        assertEquals(MailCategory.Transactions, picked)
        assertEquals("Nothing in Newsletters & promotions", tabs.emptyTitle)
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
                MailCategory.Primary to 2,
                MailCategory.Transactions to 0,
                MailCategory.Notifications to 2,
                MailCategory.Newsletters to 1,
            ),
            counts,
            "The code counts in Primary and Notifications",
        )
        val hidden = InboxCategories(enabled = true, hidden = setOf(MailCategory.Notifications)).unreadCounts(mail)
        assertEquals(3, hidden[MailCategory.Primary], "A hidden category's mail counts in Primary")
        assertEquals(null, hidden[MailCategory.Notifications])
    }

    @Test
    fun primaryCannotBeHidden() {
        val categories =
            on
                .withShown(MailCategory.Primary, shown = false)
                .withShown(MailCategory.Notifications, shown = false)
        assertEquals(MailCategory.entries - MailCategory.Notifications, categories.tabs)
        assertTrue(categories.shows(MailCategory.Primary))
        assertEquals(MailCategory.entries, categories.withShown(MailCategory.Notifications, shown = true).tabs)
    }

    private fun InboxCategories.inTab(
        messages: List<MailMessage>,
        tab: MailCategory,
    ) = CategoryTabs(this, tab, emptyMap()) {}.shown(messages)

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
