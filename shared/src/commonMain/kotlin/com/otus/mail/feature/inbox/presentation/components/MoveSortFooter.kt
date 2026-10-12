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
import com.otus.designsystem.components.ChoiceRow
import com.otus.designsystem.components.ChoiceSheet
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.elements.ValueButton
import com.otus.designsystem.theme.SheetTokens

/**
 * The condition a new rule will use, pinned below the folders while the sort pill is on. Nothing
 * while it is off.
 */
@Composable
internal fun MoveSortFooter(sorting: MoveSorting) {
    val future = sorting.future ?: return
    val chosen = sorting.rule ?: return
    var choosing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(start = SheetTokens.inset, end = SheetTokens.inset, bottom = SheetTokens.inset)) {
        SettingRow("Mail that", { choosing = true }) { ValueButton(chosen.label, wide = true) }
        SheetCaption(
            "Tap a folder. This message and future ones like it go there.",
            RowTextInset,
            heading = false,
        )
    }
    if (choosing) {
        ChoiceSheet(
            "Sort mail that…",
            ChoiceOptions(future.choices, chosen),
            row = { ChoiceRow(it.label, it.detail, it.icon, it.person?.namedByAddress) },
            onSelect = sorting.onChoose,
            onDismiss = { choosing = false },
            footnote = "Becomes a rule in ${future.accountName}. Change or delete it any time in Rules.",
        )
    }
}
