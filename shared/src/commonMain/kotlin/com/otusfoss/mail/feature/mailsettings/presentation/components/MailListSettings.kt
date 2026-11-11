package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.dateformat.currentLocalDate
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_format_list_bulleted
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.core.settings.presentation.components.ChoiceRowSpec
import com.otusfoss.core.settings.presentation.components.PageRowSpec
import com.otusfoss.core.settings.presentation.components.SettingRowSpec
import com.otusfoss.core.settings.presentation.components.SettingsSectionSpec
import com.otusfoss.core.settings.presentation.components.ToggleRowSpec
import com.otusfoss.core.settings.presentation.components.waitingOn
import com.otusfoss.mail.core.messages.presentation.dateStyleExample
import com.otusfoss.mail.core.settings.domain.DateStyle
import com.otusfoss.mail.core.settings.domain.ListOrder
import com.otusfoss.mail.core.settings.domain.MailListLayout
import com.otusfoss.mail.core.settings.domain.PreviewLines
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.SENDER_UNAVAILABLE
import com.otusfoss.mail.core.settings.presentation.label
import com.otusfoss.mail.core.settings.presentation.summary

/** Mail's row in the Appearance page's Look section, which [open]s the Mail list page. */
fun mailListRow(
    state: MailBehaviorState,
    dateFormatter: DateFormatter,
    open: (setting: String?) -> Unit,
): PageRowSpec =
    PageRowSpec(
        "Mail list",
        open,
        value = state.behavior.list.layout.summary,
        icon = Res.drawable.symbol_format_list_bulleted,
        keywords = listOf("Rows", "Density"),
    ) { mailListSettings(state, dateFormatter) }

/**
 * How the mail list's rows read. Order and Preview apply only while the sender shows, so they stay
 * below Show sender but disabled while it is off. Date styles show a sample date in
 * [dateFormatter]'s format. Each change is saved at once.
 */
fun mailListSettings(
    state: MailBehaviorState,
    dateFormatter: DateFormatter,
): List<SettingsSectionSpec> =
    listOf(
        SettingsSectionSpec("Rows", rowSettings(state)),
        SettingsSectionSpec("Dates", dateSettings(state, dateFormatter)),
    )

private fun rowSettings(state: MailBehaviorState): List<SettingRowSpec> {
    val list = state.behavior.list.layout
    val waiting = SENDER_UNAVAILABLE.takeUnless { list.showSender }
    return listOf(
        ToggleRowSpec("Show sender", list.showSender, { on -> state.changeList { it.copy(showSender = on) } }),
        ChoiceRowSpec(
            "Order",
            list.order.label,
            { OrderSheet(state, it) },
            ListOrder.entries.map { it.label },
        ).waitingOn(waiting),
        ChoiceRowSpec(
            "Preview",
            list.preview.label,
            { PreviewSheet(state, it) },
            PreviewLines.entries.map { it.label },
            keywords = listOf("Snippet", "Lines"),
        ).waitingOn(waiting),
        ToggleRowSpec("Folder icons", list.folderIcons, { on -> state.changeList { it.copy(folderIcons = on) } }),
    )
}

private fun dateSettings(
    state: MailBehaviorState,
    dateFormatter: DateFormatter,
): List<SettingRowSpec> {
    val list = state.behavior.list.layout
    return listOf(
        ChoiceRowSpec(
            "Style",
            list.dateStyle.label,
            { DateStyleSheet(state, dateFormatter, it) },
            DateStyle.entries.map { it.label },
            keywords = listOf("Relative"),
        ),
        ToggleRowSpec("Show this year", list.showThisYear, { on -> state.changeList { it.copy(showThisYear = on) } }),
    )
}

private fun MailBehaviorState.changeList(update: (MailListLayout) -> MailListLayout) {
    updateList { it.copy(layout = update(it.layout)) }
}

@Composable
private fun OrderSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        title = "Order",
        choices = ChoiceOptions(ListOrder.entries, state.behavior.list.layout.order, MailListLayout().order),
        row = { ChoiceRow(it.label) },
        onSelect = { order -> state.changeList { it.copy(order = order) } },
        onDismiss = onDismiss,
    )
}

@Composable
private fun PreviewSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        title = "Preview",
        choices = ChoiceOptions(PreviewLines.entries, state.behavior.list.layout.preview, MailListLayout().preview),
        row = { ChoiceRow(it.label) },
        onSelect = { preview -> state.changeList { it.copy(preview = preview) } },
        onDismiss = onDismiss,
    )
}

@Composable
private fun DateStyleSheet(
    state: MailBehaviorState,
    dateFormatter: DateFormatter,
    onDismiss: () -> Unit,
) {
    val list = state.behavior.list.layout
    ChoiceSheet(
        title = "Date style",
        choices = ChoiceOptions(DateStyle.entries, list.dateStyle, MailListLayout().dateStyle),
        row = { ChoiceRow(it.label, dateFormatter.dateStyleExample(list.copy(dateStyle = it), currentLocalDate())) },
        onSelect = { style -> state.changeList { it.copy(dateStyle = style) } },
        onDismiss = onDismiss,
    )
}
