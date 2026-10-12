package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ChoiceOptions
import com.otus.designsystem.components.ChoiceSheet
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SettingToggleRow
import com.otus.designsystem.elements.ValueButton
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.theme.SheetTokens

/**
 * The switch that also sorts future mail, pinned below the folders, with the condition the rule
 * will use once it is on. Nothing when the account cannot run rules.
 */
@Composable
internal fun MoveSortFooter(sorting: MoveSorting) {
    val future = sorting.future ?: return
    var choosing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(start = SheetTokens.inset, end = SheetTokens.inset, bottom = SheetTokens.inset)) {
        SettingToggleRow(
            "Also sort future mail",
            sorting.on,
            sorting.onToggle,
            description = if (sorting.on) "Adds a rule in ${future.accountName}" else "Off · only this message moves",
            icon = Res.drawable.symbol_filter_list,
        )
        val chosen = sorting.rule ?: return@Column
        SettingRow("Mail that", { choosing = true }, trailing = { ValueButton(chosen.label) })
        SheetCaption("Tap a folder. This message and future ones like it go there.", RowTextInset, heading = false)
    }
    if (choosing && sorting.chosen != null) {
        ChoiceSheet(
            "Sort mail that…",
            ChoiceOptions(future.choices, sorting.chosen),
            label = { it.label },
            onSelect = sorting.onChoose,
            onDismiss = { choosing = false },
            detail = { it.detail },
            footnote = "Becomes a rule in ${future.accountName}. Change or delete it any time in Rules.",
        )
    }
}
