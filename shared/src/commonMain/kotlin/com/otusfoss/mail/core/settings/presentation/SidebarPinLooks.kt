package com.otusfoss.mail.core.settings.presentation

import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_bookmark
import com.otusfoss.core.designsystem.resources.symbol_contacts
import com.otusfoss.core.designsystem.resources.symbol_folder
import com.otusfoss.core.designsystem.resources.symbol_forum
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.presentation.READ_LATER_TITLE
import com.otusfoss.mail.core.mailbox.presentation.symbol
import com.otusfoss.mail.core.settings.domain.SidebarPin
import org.jetbrains.compose.resources.DrawableResource

/**
 * What the sidebar, Start page, and the pin's own page call [this]; null for a folder pin, which
 * takes its folder's name.
 */
val SidebarPin.title: String?
    get() =
        when (this) {
            SidebarPin.Inbox -> MailboxRole.Inbox.title
            SidebarPin.ReadLater -> READ_LATER_TITLE
            SidebarPin.Conversations -> "Convos"
            SidebarPin.Contacts -> "Contacts"
            is SidebarPin.Role -> role.title
            is SidebarPin.Folder -> null
        }

val SidebarPin.symbol: DrawableResource
    get() =
        when (this) {
            SidebarPin.Inbox -> MailboxRole.Inbox.symbol
            SidebarPin.ReadLater -> Res.drawable.symbol_bookmark
            SidebarPin.Conversations -> Res.drawable.symbol_forum
            SidebarPin.Contacts -> Res.drawable.symbol_contacts
            is SidebarPin.Role -> role.symbol
            is SidebarPin.Folder -> Res.drawable.symbol_folder
        }
