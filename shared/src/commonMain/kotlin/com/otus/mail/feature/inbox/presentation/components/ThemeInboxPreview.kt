package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_calendar_add_on
import com.otus.designsystem.resources.symbol_edit
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_mark_email_read
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.resources.symbol_search
import com.otus.designsystem.shared.actionbar.components.ActionBar
import com.otus.designsystem.shared.actionbar.components.BarAction
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.people.elements.Avatar
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.shapes.SmoothCornerShape
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.settings.ThemeTokens

/**
 * The inbox in the current theme for the theme page: pills, three messages with the first
 * selected, and the action bar. It only shows colors, so screen readers and touches skip it.
 */
@Composable
fun ThemeInboxPreview() {
    Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(ThemeTokens.previewGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap)) {
            Chip("People", {}, selected = true, leading = { Symbol(Res.drawable.symbol_person) })
            Chip("Date", {}, leading = { Symbol(Res.drawable.symbol_calendar_add_on) })
        }
        Column {
            previewMessages.forEachIndexed { index, message -> PreviewMessage(message, selected = index == 0) }
        }
        ActionBar(
            primary = BarAction("New mail", Res.drawable.symbol_edit) {},
            secondary =
                listOf(
                    BarAction("Search", Res.drawable.symbol_search) {},
                    BarAction("Filter", Res.drawable.symbol_filter_list) {},
                    BarAction("Mark all read", Res.drawable.symbol_mark_email_read) {},
                ),
        )
    }
}

private class SampleMessage(
    val initials: String,
    val from: String,
    val time: String,
    val subject: String,
)

private val previewMessages =
    listOf(
        SampleMessage("GH", "Grace Hopper", "10:42", "Q4 launch plan"),
        SampleMessage("AB", "Acme Billing", "09:15", "Invoice 1042 for September"),
        SampleMessage("MK", "Mara König", "Sat", "Notes from Thursday"),
    )

@Composable
private fun PreviewMessage(
    message: SampleMessage,
    selected: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) OtusColors.accentDark else OtusColors.bgRegular,
                SmoothCornerShape(ThemeTokens.previewRowRadius),
            ).padding(ThemeTokens.previewRowPadding),
        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.previewRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(message.initials, size = ThemeTokens.previewAvatar, fill = OtusColors.bgDark)
        Column(Modifier.weight(1f)) {
            Row {
                val detail = if (selected) OtusColors.text1 else OtusColors.text2
                Text(message.from, Modifier.weight(1f), color = detail, fontSize = ThemeTokens.previewDetailSize)
                Text(message.time, color = detail, fontSize = ThemeTokens.previewDetailSize)
            }
            Text(
                message.subject,
                fontSize = ThemeTokens.previewSubjectSize,
                lineHeight = ThemeTokens.previewSubjectLineHeight,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
