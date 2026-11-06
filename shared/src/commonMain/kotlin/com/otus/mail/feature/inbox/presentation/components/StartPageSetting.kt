package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_home
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.core.mailbox.pathText
import com.otus.mail.core.settings.domain.SidebarPin
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.SidebarPinOption

/** The Start page row, which names the page Mail opens on among [options] and calls [onChoose]. */
@Composable
internal fun StartPageRow(
    start: SidebarPin,
    options: List<SidebarPinOption>,
    onChoose: () -> Unit,
) {
    SettingRow("Start page", onChoose, icon = Res.drawable.symbol_home) {
        ValueButton(startPageLabel(start, options))
    }
}

/**
 * Picks the page Mail opens on among [options], the sidebar's mailboxes, with the Inbox first as
 * the default. A saved choice no account has now selects nothing, since Mail opens on the Inbox.
 */
@Composable
internal fun StartPageSheet(
    state: MailBehaviorState,
    options: List<SidebarPinOption>,
    onDismiss: () -> Unit,
) {
    val pins = options.map { it.pin }
    ChoiceSheet(
        "Start page",
        ChoiceOptions(pins, selected = state.behavior.startPage, default = SidebarPin.Inbox),
        row = { pin -> options.first { it.pin == pin }.startPageRow() },
        onSelect = { pin -> state.update { it.copy(startPage = pin) } },
        onDismiss = onDismiss,
    )
}

/** The Start page row's value: the chosen page, or Inbox while no account has it. */
internal fun startPageLabel(
    start: SidebarPin,
    options: List<SidebarPinOption>,
): String = options.firstOrNull { it.pin == start }?.title ?: INBOX_TITLE

private fun SidebarPinOption.startPageRow(): ChoiceRow = ChoiceRow(path.pathText(), icon = icon, iconTint = tint)

private const val INBOX_TITLE = "Inbox"
