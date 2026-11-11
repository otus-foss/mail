package com.otusfoss.mail.feature.folders.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.mail.folders.components.RuleChip
import com.otusfoss.core.designsystem.shared.controls.elements.Chip
import com.otusfoss.core.designsystem.shared.people.components.PersonAvatar
import com.otusfoss.core.designsystem.shared.selection.components.PickMark
import com.otusfoss.core.designsystem.shared.selection.components.PickRow
import com.otusfoss.core.designsystem.shared.selection.components.PickStyle
import com.otusfoss.core.designsystem.shared.settings.components.SettingToggleRow
import com.otusfoss.core.designsystem.theme.colors.OtusColors
import com.otusfoss.core.designsystem.theme.tokens.core.Spacing
import com.otusfoss.core.designsystem.theme.tokens.overlays.SheetTokens
import com.otusfoss.core.designsystem.theme.tokens.people.AvatarTokens
import com.otusfoss.mail.feature.folders.domain.RuleCondition
import com.otusfoss.mail.feature.folders.presentation.RuleKind
import com.otusfoss.mail.feature.folders.presentation.RuleSuggestion
import com.otusfoss.mail.feature.folders.presentation.chipText

/** From, To, Subject, and Mailing list, with [kind] selected. */
@Composable
internal fun RuleKindChips(
    kind: RuleKind,
    onChoose: (RuleKind) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.inlineGap)) {
        RuleKind.entries.forEach { option ->
            Chip(option.label, { onChoose(option) }, selected = option == kind)
        }
    }
}

/** The suggestions, each with the avatar its chip shows, and [chosen] selected. */
@Composable
internal fun RuleSuggestionList(
    suggestions: List<RuleSuggestion>,
    chosen: RuleCondition?,
    onChoose: (RuleCondition) -> Unit,
) {
    if (suggestions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(SheetTokens.rowPadding)) {
        suggestions.forEach { suggestion ->
            PickRow(
                suggestion.title,
                suggestion.subtitle,
                PickMark(suggestion.condition == chosen, style = PickStyle.Card),
                { onChoose(suggestion.condition) },
                leading = { SuggestionAvatar(suggestion.condition) },
            )
        }
    }
}

@Composable
private fun SuggestionAvatar(condition: RuleCondition) {
    val avatar = condition.chipText().chipLine().avatar ?: return
    PersonAvatar(avatar, AvatarTokens.size)
}

/** The chip [condition] adds to the folder, under what it does. */
@Composable
internal fun RulePreview(
    folderName: String,
    condition: RuleCondition,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SheetTokens.rowPadding)) {
        Text(
            "New mail that matches goes to $folderName",
            color = OtusColors.text2,
            fontSize = SheetTokens.detailSize,
        )
        RuleChip(condition.chipText().chipLine())
    }
}

/** "Move the N that are already here", shown only while [count] is more than zero. */
@Composable
internal fun MoveExistingSwitch(
    count: Int?,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    if (count == null || count <= 0) return
    SettingToggleRow("Move the $count that are already here", on, onChange, description = "From Inbox and Archive")
}
