package com.otusfoss.mail

import com.otusfoss.mail.core.categories.domain.CategoryVerdict
import com.otusfoss.mail.core.categories.domain.MailCategory
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.AccountAddresses
import com.otusfoss.mail.core.messages.domain.MailAttributes
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailScopeChoice
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.MailboxScope
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.core.messages.presentation.components.MailScopeSwitch
import com.otusfoss.mail.core.settings.domain.AddressPins
import com.otusfoss.mail.feature.inbox.presentation.MailboxSearch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

/** The control panel design's inbox: enough mail to scroll, and two accounts for the stack. */
internal object ControlPanelFixtures {
    /** Mail dates count back from [PinnedToday], which the scenes give the inbox too. */
    private val today = PinnedToday

    val messages: List<MailMessage> =
        listOf(
            message("1", "Grace Hopper", "Q4 launch plan", 0, unread = true, files = true),
            message("2", "Acme Billing", "Invoice 1042 for September", 0, category = MailCategory.Transactions),
            message("3", "Mara König", "Notes from Thursday", 2, unread = true),
            message("4", "Katherine Johnson", "Re: Launch numbers", 3),
            message("5", "Legal team", "German terms review", 8),
            message("6", "Alan Turing", "Lunch on Friday?", 9),
            message("7", "Radia Perlman", "Spanning tree slides", 10),
            message("8", "Grace Hopper", "Re: COBOL review notes", 10),
            message("9", "Sentry", "Weekly report for Otus", 11, unread = true, category = MailCategory.Notifications),
            message("10", "Donald Knuth", "TAOCP errata", 12),
            message("11", "Barbara Liskov", "Abstraction talk", 13),
            message("12", "Edsger Dijkstra", "Go To considered", 14),
            message("13", "Hedy Lamarr", "Frequency hopping", 15),
            message("14", "Katherine Johnson", "Launch checklist v3", 16),
        )

    private val accounts =
        listOf(
            AccountAddresses("otus", "otus@rubylabs.design", listOf("otus@rubylabs.design")),
            AccountAddresses(
                "flo",
                "florian@rubylabs.design",
                listOf("florian@rubylabs.design", "florian@purbank.example", "hi@rubylabs.design"),
            ),
        )

    /** What the search's Accounts sheet offers: both accounts, and three of their mailboxes. */
    val mailboxSearch =
        MailboxSearch(
            listOf(
                MailboxScope.Inbox,
                MailboxScope(null, "Archive", MailboxRole.Archive),
                MailboxScope(null, "Sent", MailboxRole.Sent),
            ),
            accounts.map { MailLocation("", it.name, it.accountId, it.name) },
        )

    /** Every account's mail, with Florian's account counting 5 unread. */
    fun scope(onChoose: (MailScopeChoice) -> Unit = {}): MailScopeSwitch =
        MailScopeSwitch(
            accounts,
            MailScopeChoice.All,
            AddressPins(listOf(MailScopeChoice.Address("flo", "florian@purbank.example"))),
            count = { if (it == MailScopeChoice.All || it.accountId == "flo") "5" else null },
            onChoose = onChoose,
            onTogglePin = {},
        )

    private fun message(
        id: String,
        from: String,
        subject: String,
        daysAgo: Int,
        unread: Boolean = false,
        files: Boolean = false,
        category: MailCategory? = null,
    ) = MailMessage(
        id,
        MailSender(from, "${from.lowercase().replace(' ', '.')}@example.org"),
        subject,
        "A short preview of the message.",
        ReceivedAt(today.minus(DatePeriod(days = daysAgo)).toString(), "10:42"),
        MailLocation("Inbox", "otus@rubylabs.design", accountId = "otus", mailboxRole = MailboxRole.Inbox),
        MailAttributes(unread = unread, attachments = if (files) listOf("plan.pdf") else emptyList()),
        to = listOf(MailSender("", "otus@rubylabs.design")),
        category = category?.let { CategoryVerdict(it, emptyList()) },
    )
}
