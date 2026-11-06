package com.otus.mail

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_send
import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailAttributes
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.OwnAddress
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.core.messages.domain.SenderKey
import com.otus.mail.feature.inbox.presentation.FilterAccount
import com.otus.mail.feature.inbox.presentation.FilterAddress
import com.otus.mail.feature.inbox.presentation.FilterFolder
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FolderKey
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import org.jetbrains.compose.resources.DrawableResource

/** Demo mail and folders for filter panel tests: names repeat, and Work nests five levels deep. */
internal object FilterPanelFixtures {
    /** Mail dates count back from [PinnedToday], which the scenes give the inbox too. */
    private val today = PinnedToday

    private val folders =
        listOf(
            system("inbox", "Inbox", Res.drawable.symbol_inbox),
            system("sent", "Sent", Res.drawable.symbol_send),
            system("trash", "Trash", Res.drawable.symbol_delete),
            folder("clients", "Clients", color = FolderColor.Blue, below = setOf("acme")),
            folder("acme", "Acme", "Clients", parent = "clients", color = FolderColor.Blue),
            folder("freelancing", "Freelancing", below = setOf("free-invoices")),
            folder("free-invoices", "Invoices", "Freelancing", parent = "freelancing", color = FolderColor.Pink),
            folder("finance", "Finance", below = setOf("fin-invoices")),
            folder("fin-invoices", "Invoices", "Finance", parent = "finance", color = FolderColor.Yellow),
            folder("dev", "Dev", color = FolderColor.Purple),
            folder("travel", "Travel", color = FolderColor.Teal),
            folder("work", "Work", below = setOf("projects", "2026", "q4", "work-invoices")),
            folder("projects", "Projects", "Work", parent = "work", below = setOf("2026", "q4", "work-invoices")),
            folder("2026", "2026", "Work", "Projects", parent = "projects", below = setOf("q4", "work-invoices")),
            folder("q4", "Q4", "Work", "Projects", "2026", parent = "2026", below = setOf("work-invoices")),
            folder("work-invoices", "Invoices", "Work", "Projects", "2026", "Q4", parent = "q4"),
        )

    val options =
        FilterOptions(
            addresses =
                listOf(
                    FilterAddress(OwnAddress("ada", "this@companyA.com"), ChipAvatar("CA", colorIndex = 2)),
                    FilterAddress(OwnAddress("ada", "otus@rubylabs.design"), ChipAvatar("RL", colorIndex = 5)),
                ),
            accounts = listOf(FilterAccount("ada", "Ada", ChipAvatar("A", colorIndex = 1), folders)),
            folders = true,
            readLater = true,
        )

    /** [options] with a second login, so the Account row and page show. */
    val twoAccounts =
        options.copy(
            addresses =
                options.addresses +
                    FilterAddress(OwnAddress("flo", "florian@rubylabs.design"), ChipAvatar("FR", colorIndex = 3)),
            accounts =
                options.accounts + FilterAccount("flo", "Personal", ChipAvatar("P", colorIndex = 3), emptyList()),
        )

    val grace = SenderKey("Grace Hopper", setOf("grace@navy.example"))

    /** Both of ChatGPT's addresses, which the From line merges into one person. */
    val chatGpt = SenderKey("ChatGPT", setOf("noreply@openai.com", "updates@openai.com"))

    val messages =
        listOf(
            message("1", "Sam Rivera", "Invoice for October", daysAgo = 0, unread = true),
            message("2", "Acme Corp", "Kickoff notes", daysAgo = 1),
            message("3", "Lena Park", "Trip itinerary", daysAgo = 3),
            message("4", "Grace Hopper", "Compiler notes", daysAgo = 4, address = "grace@navy.example"),
            message("5", "Grace Hopper", "Re: Compiler notes", daysAgo = 5, address = "grace@navy.example"),
            message("6", "ChatGPT", "Your weekly summary", daysAgo = 6, address = "noreply@openai.com"),
            message("7", "ChatGPT", "New features", daysAgo = 7, address = "updates@openai.com"),
        )

    private fun system(
        id: String,
        name: String,
        icon: DrawableResource,
    ) = FilterFolder(FolderKey("ada", id), name, emptyList(), icon, system = true)

    private fun folder(
        id: String,
        name: String,
        vararg ancestors: String,
        parent: String? = null,
        color: FolderColor = FolderColor.Default,
        below: Set<String> = emptySet(),
    ) = FilterFolder(
        FolderKey("ada", id),
        name,
        ancestors.toList(),
        Res.drawable.symbol_folder,
        color,
        system = false,
        parent,
        below,
    )

    private fun message(
        id: String,
        from: String,
        subject: String,
        daysAgo: Int,
        unread: Boolean = false,
        address: String = "${from.lowercase().replace(' ', '.')}@example.org",
    ) = MailMessage(
        id,
        MailSender(from, address),
        subject,
        "A short preview of the message.",
        ReceivedAt(today.minus(DatePeriod(days = daysAgo)).toString(), "09:30"),
        MailLocation("Inbox", "Ada", accountId = "ada", mailboxRole = MailboxRole.Inbox),
        MailAttributes(unread = unread),
        to = listOf(MailSender("", "otus@rubylabs.design")),
    )
}
