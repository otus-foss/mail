package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
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
import com.otus.designsystem.components.ValueToken
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
    Column(
        Modifier.padding(SheetTokens.inset),
        verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
    ) {
        SheetCaption("Create new rule based on this move action", RowTextInset)
        ValueToken(
            chosen.value,
            keyword = chosen.keyword,
            avatar = chosen.person?.chipAvatar,
            onClick = { choosing = true },
        )
        SheetCaption("This message and future ones like it go there.", RowTextInset, heading = false)
    }
    if (choosing) {
        ChoiceSheet(
            "Sort mail that…",
            ChoiceOptions(future.choices, chosen),
            row = { ChoiceRow(it.value, it.detail, person = it.person?.namedByAddress, keyword = it.keyword) },
            onSelect = sorting.onChoose,
            onDismiss = { choosing = false },
            footnote = "Becomes a rule in ${future.accountName}. Change or delete it any time in Rules.",
        )
    }
}
