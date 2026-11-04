package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_inbox
import com.otus.mail.feature.inbox.domain.MailboxStyle
import com.otus.mail.feature.inbox.domain.SidebarCount
import com.otus.mail.feature.inbox.domain.SidebarLayout
import com.otus.mail.feature.inbox.domain.SidebarSection
import org.jetbrains.compose.resources.DrawableResource

val SidebarSection.title: String
    get() =
        when (this) {
            SidebarSection.Mailboxes -> "Pinned"
            SidebarSection.Folders -> "Folders"
        }

val MailboxStyle.label: String
    get() =
        when (this) {
            MailboxStyle.Grid -> "Grid"
            MailboxStyle.Rows -> "Rows"
        }

val SidebarCount.label: String
    get() =
        when (this) {
            SidebarCount.Unread -> "Unread"
            SidebarCount.Total -> "Total"
            SidebarCount.None -> "Off"
        }

/** The Sidebar row's summary on the Mail behavior page: how many sections it shows. */
val SidebarLayout.sectionSummary: String
    get() = if (shown.size == 1) "1 section" else "${shown.size} sections"

val SidebarSection.icon: DrawableResource
    get() =
        when (this) {
            SidebarSection.Mailboxes -> Res.drawable.symbol_inbox
            SidebarSection.Folders -> Res.drawable.symbol_folder
        }

/** The Pinned row's summary: how the pins show and how many there are, as "Grid, 3 pinned". */
fun pinnedSummary(
    style: MailboxStyle,
    count: Int,
): String = "${style.label}, ${if (count == 0) "nothing pinned" else "$count pinned"}"

/** Why the Pinned row is disabled while the sidebar hides its Pinned section; search shows it too. */
const val PINNED_UNAVAILABLE = "Turn on the Pinned section to arrange it"
