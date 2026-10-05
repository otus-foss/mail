package com.otus.mail.app

import androidx.compose.runtime.Composable
import com.otus.mail.designsystem.components.Sidebar
import com.otus.mail.designsystem.elements.SidebarEntry

@Composable
internal fun MailSidebar(
    open: Boolean,
    current: MailDestination,
    onSelect: (MailDestination) -> Unit,
    onDismiss: () -> Unit,
) {
    Sidebar(
        open = open,
        onDismiss = onDismiss,
        title = "Otus",
        footer = { MailSidebarEntry(MailDestination.Settings, current, onSelect) },
    ) {
        MailSidebarEntry(MailDestination.Inbox, current, onSelect)
    }
}

@Composable
private fun MailSidebarEntry(
    destination: MailDestination,
    current: MailDestination,
    onSelect: (MailDestination) -> Unit,
) {
    SidebarEntry(destination.label, destination.icon, selected = destination == current, onClick = {
        onSelect(destination)
    })
}
