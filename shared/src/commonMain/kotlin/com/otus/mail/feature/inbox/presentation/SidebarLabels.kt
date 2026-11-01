package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailboxStyle
import com.otus.mail.feature.inbox.domain.SidebarCount
import com.otus.mail.feature.inbox.domain.SidebarLayout
import com.otus.mail.feature.inbox.domain.SidebarSection

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

/** The Sidebar row's summary on the appearance page: how many sections it shows. */
val SidebarLayout.sectionSummary: String
    get() = if (shown.size == 1) "1 section" else "${shown.size} sections"
