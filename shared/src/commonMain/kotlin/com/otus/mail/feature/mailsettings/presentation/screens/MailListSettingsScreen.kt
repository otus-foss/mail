package com.otus.mail.feature.mailsettings.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.dateformat.DateFormatter
import com.otus.dateformat.currentLocalDate
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.designsystem.shared.settings.components.DependentSettings
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.core.messages.presentation.dateStyleExample
import com.otus.mail.core.settings.domain.DateStyle
import com.otus.mail.core.settings.domain.ListOrder
import com.otus.mail.core.settings.domain.MailListLayout
import com.otus.mail.core.settings.domain.PreviewLines
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.SENDER_UNAVAILABLE
import com.otus.mail.core.settings.presentation.label

/**
 * How the mail list's rows read, with [preview] showing a row in the current layout at the top.
 * Order and Preview apply only while the sender shows, so they stay below Show sender but disabled
 * while it is off. Date styles show a sample date in [dateFormatter]'s format. Each change is
 * saved at once.
 */
@Composable
fun MailListSettingsScreen(
    state: MailBehaviorState,
    dateFormatter: DateFormatter,
    preview: @Composable (MailListLayout) -> Unit,
    onBack: () -> Unit,
) {
    val list = state.behavior.list
    val change: ((MailListLayout) -> MailListLayout) -> Unit = { update ->
        state.update { it.copy(list = update(it.list)) }
    }
    var choosing by rememberSaveable { mutableStateOf<ListChoice?>(null) }
    val example = { layout: MailListLayout -> dateFormatter.dateStyleExample(layout, currentLocalDate()) }
    Subpage("Mail list", onBack) {
        preview(list)
        SettingsSection("Rows") {
            SettingToggleRow(
                "Show sender",
                checked = list.showSender,
                onCheckedChange = { on -> change { it.copy(showSender = on) } },
            )
            val waiting = SENDER_UNAVAILABLE.takeUnless { list.showSender }
            DependentSettings(enabled = list.showSender) {
                SettingRow("Order", { choosing = ListChoice.Order }, description = waiting) {
                    ValueButton(list.order.label)
                }
                SettingRow("Preview", { choosing = ListChoice.Preview }, description = waiting) {
                    ValueButton(list.preview.label)
                }
            }
            SettingToggleRow(
                "Folder icons",
                checked = list.folderIcons,
                onCheckedChange = { on -> change { it.copy(folderIcons = on) } },
            )
        }
        SettingsSection("Dates") {
            SettingRow("Style", { choosing = ListChoice.Date }) { ValueButton(list.dateStyle.label) }
            SettingToggleRow(
                "Show this year",
                checked = list.showThisYear,
                onCheckedChange = { on -> change { it.copy(showThisYear = on) } },
            )
        }
    }
    choosing?.let { ListChoiceSheet(it, list, example, change) { choosing = null } }
}

@Composable
private fun ListChoiceSheet(
    choice: ListChoice,
    list: MailListLayout,
    example: (MailListLayout) -> String,
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
                title = "Date style",
                choices = ChoiceOptions(DateStyle.entries, selected = list.dateStyle, default = defaults.dateStyle),
                row = { ChoiceRow(it.label, example(list.copy(dateStyle = it))) },
                onSelect = { style -> change { it.copy(dateStyle = style) } },
                onDismiss = onDismiss,
            )
        }
    }
}

private enum class ListChoice { Order, Preview, Date }
