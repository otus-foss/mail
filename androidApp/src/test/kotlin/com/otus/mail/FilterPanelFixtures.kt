package com.otus.mail

import com.otus.designsystem.components.ChipAvatar
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_send
import com.otus.designsystem.theme.FolderColor
import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.OwnAddress
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.FilterAccount
import com.otus.mail.feature.inbox.presentation.FilterAddress
import com.otus.mail.feature.inbox.presentation.FilterFolder
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FolderKey
import org.jetbrains.compose.resources.DrawableResource

/** Demo mail and folders for filter panel tests: names repeat, and Work nests five levels deep. */
internal object FilterPanelFixtures {
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
            shortcuts =
                listOf(
                    "clients",
                    "acme",
                    "free-invoices",
                    "fin-invoices",
                    "dev",
                    "travel",
                ).map { FolderKey("ada", it) },
            folders = true,
            readLater = true,
        )

    val messages =
        listOf(
            message("1", "Sam Rivera", "Invoice for October", "2026-10-18", unread = true),
            message("2", "Acme Corp", "Kickoff notes", "2026-10-17"),
            message("3", "Lena Park", "Trip itinerary", "2026-10-15"),
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
        false,
        parent,
        below,
    )

    private fun message(
        id: String,
        from: String,
        subject: String,
        date: String,
        unread: Boolean = false,
    ) = MailMessage(
        id,
        MailSender(from, "${from.lowercase().replace(' ', '.')}@example.org"),
        subject,
        "A short preview of the message.",
        ReceivedAt(date, "09:30"),
        MailLocation("Inbox", "Ada"),
        MailAttributes(unread = unread),
    )
}
