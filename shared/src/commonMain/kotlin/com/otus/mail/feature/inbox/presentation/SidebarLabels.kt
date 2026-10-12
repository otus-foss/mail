package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailboxStyle
import com.otus.mail.feature.inbox.domain.SidebarCount
import com.otus.mail.feature.inbox.domain.SidebarSection

val SidebarSection.title: String
    get() =
        when (this) {
            SidebarSection.Mailboxes -> "Mailboxes"
            SidebarSection.Folders -> "Folders"
        }

val MailboxStyle.label: String
    get() =
        when (this) {
            MailboxStyle.Chips -> "Chips"
            MailboxStyle.Rows -> "Rows"
        }

val SidebarCount.label: String
    get() =
        when (this) {
            SidebarCount.Unread -> "Unread"
            SidebarCount.Total -> "Total"
            SidebarCount.None -> "Off"
        }
