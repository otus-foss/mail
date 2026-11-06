package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.rows.elements.Caption
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.designsystem.shared.selection.components.ValuePicker
import com.otus.designsystem.theme.tokens.overlays.SheetTokens

/**
 * The condition a new rule will use, pinned below the folders while the sort pill is on. Nothing
 * while it is off.
 */
@Composable
internal fun MoveSortFooter(sorting: MoveSorting) {
    val future = sorting.future ?: return
    val chosen = sorting.rule ?: return
    var choosing by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.padding(SheetTokens.inset),
        verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
    ) {
        Caption("New rule", RowTextInset)
        ValuePicker(
            chosen.value,
            { choosing = true },
            Modifier.fillMaxWidth(),
            keyword = chosen.keyword,
            avatar = chosen.person?.chipAvatar,
        )
    }
    if (choosing) {
        ChoiceSheet(
            "Sort mail that…",
            ChoiceOptions(future.choices, chosen),
            row = { ChoiceRow(it.value, it.detail, person = it.person?.namedByAddress, keyword = it.keyword) },
            onSelect = sorting.onChoose,
            onDismiss = { choosing = false },
            footnote = "Becomes a rule in ${future.accountName}.",
        )
    }
}
