package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.components.ChoiceOptions
import com.otus.designsystem.components.ChoiceRow
import com.otus.designsystem.components.ChoiceSheet
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SettingToggleRow
import com.otus.designsystem.components.SettingsSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.elements.ValueButton
import com.otus.mail.feature.inbox.domain.DateStyle
import com.otus.mail.feature.inbox.domain.ListOrder
import com.otus.mail.feature.inbox.domain.MailListLayout
import com.otus.mail.feature.inbox.domain.PreviewLines
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.detail
import com.otus.mail.feature.inbox.presentation.label

/**
 * How the mail list's rows read, with [preview] showing a row in the current layout at the top.
 * Order and Preview apply only while the sender shows, so they hide while Show sender is off.
 * Each change is saved at once.
 */
@Composable
fun MailListSettingsScreen(
    state: MailBehaviorState,
    preview: @Composable (MailListLayout) -> Unit,
    onBack: () -> Unit,
) {
    val list = state.behavior.list
    val change: ((MailListLayout) -> MailListLayout) -> Unit = { update ->
        state.update { it.copy(list = update(it.list)) }
    }
    var choosing by rememberSaveable { mutableStateOf<ListChoice?>(null) }
    Subpage("Mail list", onBack) {
        preview(list)
        SettingsSection("Rows") {
            if (list.showSender) {
                SettingRow("Order", { choosing = ListChoice.Order }) { ValueButton(list.order.label) }
            }
            SettingToggleRow(
                "Show sender",
                checked = list.showSender,
                onCheckedChange = { on -> change { it.copy(showSender = on) } },
                description = "When off, the preview takes its line",
            )
            if (list.showSender) {
                SettingRow("Preview", { choosing = ListChoice.Preview }) { ValueButton(list.preview.label) }
            }
            SettingToggleRow(
                "Folder icons",
                checked = list.folderIcons,
                onCheckedChange = { on -> change { it.copy(folderIcons = on) } },
                description = "Show which folder each mail is in",
            )
            SettingRow("Date", { choosing = ListChoice.Date }) { ValueButton(list.dateStyle.label) }
        }
    }
    choosing?.let { ListChoiceSheet(it, list, change) { choosing = null } }
}

@Composable
private fun ListChoiceSheet(
    choice: ListChoice,
    list: MailListLayout,
    change: ((MailListLayout) -> MailListLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaults = MailListLayout()
    when (choice) {
        ListChoice.Order -> {
            ChoiceSheet(
                title = "Order",
                choices = ChoiceOptions(ListOrder.entries, selected = list.order, default = defaults.order),
                row = { ChoiceRow(it.label) },
                onSelect = { order -> change { it.copy(order = order) } },
                onDismiss = onDismiss,
            )
        }

        ListChoice.Preview -> {
            ChoiceSheet(
                title = "Preview",
                choices = ChoiceOptions(PreviewLines.entries, selected = list.preview, default = defaults.preview),
                row = { ChoiceRow(it.label) },
                onSelect = { preview -> change { it.copy(preview = preview) } },
                onDismiss = onDismiss,
            )
        }

        ListChoice.Date -> {
            ChoiceSheet(
                title = "Date",
                choices = ChoiceOptions(DateStyle.entries, selected = list.dateStyle, default = defaults.dateStyle),
                row = { ChoiceRow(it.label, it.detail) },
                onSelect = { style -> change { it.copy(dateStyle = style) } },
                onDismiss = onDismiss,
            )
        }
    }
}

private enum class ListChoice { Order, Preview, Date }
