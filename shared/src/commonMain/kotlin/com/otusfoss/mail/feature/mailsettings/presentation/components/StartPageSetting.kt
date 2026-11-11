package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_home
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.core.settings.presentation.components.ChoiceRowSpec
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.pathText
import com.otusfoss.mail.core.settings.domain.SidebarPin
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.SidebarPinOption
import com.otusfoss.mail.core.settings.presentation.icon

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
