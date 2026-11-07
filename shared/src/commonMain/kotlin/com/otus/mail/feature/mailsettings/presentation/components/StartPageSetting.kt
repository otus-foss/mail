package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_home
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.pathText
import com.otus.mail.core.settings.domain.SidebarPin
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.SidebarPinOption
import com.otus.mail.core.settings.presentation.icon
import com.otus.settings.presentation.components.ChoiceRowSpec

/** The Start page row, which names the page Mail opens on among [options], the sidebar's mailboxes. */
internal fun startPageRow(
    state: MailBehaviorState,
    options: List<SidebarPinOption>,
): ChoiceRowSpec =
    ChoiceRowSpec(
        "Start page",
        startPageLabel(state.behavior.sidebar.startPage, options),
        { StartPageSheet(state, options, it) },
        options.map { it.path.pathText() },
        Res.drawable.symbol_home,
        keywords = listOf("Open on", "Launch", "Home"),
    )

/**
 * Picks the page Mail opens on among [options], the sidebar's mailboxes, with the Inbox first as
 * the default. A saved choice no account has now selects nothing, since Mail opens on the Inbox.
 */
@Composable
private fun StartPageSheet(
    state: MailBehaviorState,
    options: List<SidebarPinOption>,
    onDismiss: () -> Unit,
) {
    val pins = options.map { it.pin }
    ChoiceSheet(
        "Start page",
        ChoiceOptions(pins, selected = state.behavior.sidebar.startPage, default = SidebarPin.Inbox),
        row = { pin -> options.first { it.pin == pin }.startPageRow() },
        onSelect = { pin -> state.updateSidebar { it.copy(startPage = pin) } },
        onDismiss = onDismiss,
    )
}

/** The Start page row's value: the chosen page, or Inbox while no account has it. */
private fun startPageLabel(
    start: SidebarPin,
    options: List<SidebarPinOption>,
): String = options.firstOrNull { it.pin == start }?.title ?: MailboxRole.Inbox.title

private fun SidebarPinOption.startPageRow(): ChoiceRow = ChoiceRow(path.pathText(), icon = icon, iconTint = tint)
