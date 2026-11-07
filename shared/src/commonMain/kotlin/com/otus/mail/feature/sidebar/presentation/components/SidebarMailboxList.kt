package com.otus.mail.feature.sidebar.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.otus.designsystem.mail.sidebar.components.SidebarMailboxGrid
import com.otus.designsystem.mail.sidebar.elements.SidebarInboxEntry
import com.otus.designsystem.mail.sidebar.elements.SidebarMailboxTile
import com.otus.designsystem.shared.controls.elements.LocalIconTint
import com.otus.designsystem.shared.sidebar.elements.SidebarCount
import com.otus.designsystem.shared.sidebar.elements.SidebarEntry
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.core.mailbox.SharedFolder
import com.otus.mail.core.settings.domain.MailboxStyle
import com.otus.mail.core.settings.domain.SidebarPin
import com.otus.mail.core.settings.presentation.tileSize
import com.otus.mail.feature.sidebar.presentation.SidebarCounter
import com.otus.mail.feature.sidebar.presentation.SidebarMailbox

/**
 * Every mailbox as a tile at its size in a grid, or as rows with the Inbox as a large one, as
 * [style] chooses. [colorOf] names a pinned folder's icon color.
 */
@Composable
internal fun SidebarMailboxList(
    mailboxes: List<SidebarMailbox>,
    style: MailboxStyle,
    current: SidebarPin?,
    counter: SidebarCounter,
    colorOf: (SharedFolder) -> String?,
    onSelect: (SidebarPin) -> Unit,
) {
    when (style) {
        MailboxStyle.Grid -> {
            SidebarMailboxGrid(mailboxes, sizeOf = { it.size.tileSize }) { mailbox ->
                mailbox.Tinted(colorOf) {
                    SidebarMailboxTile(
                        mailbox.title,
                        mailbox.icon,
                        selected = current == mailbox.pin,
                        onClick = { onSelect(mailbox.pin) },
                        count = counter.badge(mailbox.folders),
                        size = mailbox.size.tileSize,
                    )
                }
            }
        }

        MailboxStyle.Rows -> {
            mailboxes.forEach { mailbox ->
                if (mailbox.pin == SidebarPin.Inbox) {
                    SidebarInboxEntry(
                        mailbox.title,
                        mailbox.icon,
                        selected = current == mailbox.pin,
                        onClick = { onSelect(mailbox.pin) },
                        count = counter.of(mailbox.folders),
                    )
                } else {
                    mailbox.Tinted(colorOf) {
                        SidebarEntry(
                            mailbox.title,
                            mailbox.icon,
                            selected = current == mailbox.pin,
                            onClick = { onSelect(mailbox.pin) },
                            trailing = { counter.of(mailbox.folders)?.let { SidebarCount(it) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarMailbox.Tinted(
    colorOf: (SharedFolder) -> String?,
    content: @Composable () -> Unit,
) {
    val tint = shared?.let { FolderColor.named(colorOf(it)).tint }
    CompositionLocalProvider(LocalIconTint provides tint, content = content)
}
