package com.otus.mail.feature.folders.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.overlays.components.SheetHeaderAction
import com.otus.designsystem.shared.selection.components.PickerSearch
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.presentation.KnownAddress
import com.otus.mail.feature.folders.presentation.RuleSheetActions
import com.otus.mail.feature.folders.presentation.RuleSheetDraft
import com.otus.mail.feature.folders.presentation.ruleSuggestions
import com.otus.mail.feature.folders.presentation.typedCondition
import kotlinx.coroutines.delay

/**
 * "Sort into [folderName]": pick a kind of rule, then an address from [known] mail or the typed
 * text, and see the chip it adds. When the server finds mail already in the Inbox or Archive that
 * matches, a switch offers to move it too.
 */
@Composable
fun AddRuleSheet(
    folderName: String,
    start: RuleSheetDraft,
    known: List<KnownAddress>,
    actions: RuleSheetActions,
) {
    var kind by remember { mutableStateOf(start.kind) }
    var query by remember { mutableStateOf(start.query) }
    var picked by remember { mutableStateOf(start.chosen) }
    var moveExisting by remember { mutableStateOf(true) }
    var deleting by remember { mutableStateOf(false) }
    val suggestions = remember(kind, query, known) { ruleSuggestions(kind, query, known) }
    val chosen =
        if (kind.field == null) {
            typedCondition(kind, query)
        } else {
            picked?.takeIf { condition -> suggestions.any { it.condition == condition } }
        }
    val existing = rememberExistingCount(chosen, actions.existingCount)
    SheetFrame(
        "Sort into $folderName",
        actions.onDismiss,
        onDone = {
            when {
                deleting -> actions.onDelete?.invoke()
                chosen != null -> actions.onSave(FolderRule(chosen), moveExisting && (existing ?: 0) > 0)
            }
        },
        doneEnabled = chosen != null,
        doneLabel = if (start.editing == null) "Add" else "Save",
        leadingAction =
            actions.onDelete?.let {
                { done ->
                    SheetHeaderAction("Delete rule", {
                        deleting = true
                        done()
                    })
                }
            },
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = SheetTokens.inset, end = SheetTokens.inset, bottom = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            RuleKindChips(kind) {
                kind = it
                picked = null
            }
            PickerSearch(query, { query = it }, kind.placeholder)
            RuleSuggestionList(suggestions, chosen) { picked = it }
            chosen?.let { RulePreview(folderName, it) }
            MoveExistingSwitch(existing, moveExisting) { moveExisting = it }
        }
    }
}

/**
 * How many Inbox and Archive messages [condition] matches, asked once typing pauses; null while
 * there is nothing to count or the server cannot tell.
 */
@Composable
private fun rememberExistingCount(
    condition: RuleCondition?,
    count: suspend (FolderRule) -> Int?,
): Int? {
    var found by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(condition) {
        found = null
        if (condition != null) {
            delay(COUNT_PAUSE_MILLIS)
            found = count(FolderRule(condition))
        }
    }
    return found
}

/** Typing pauses this long before the server counts the matching mail. */
private const val COUNT_PAUSE_MILLIS = 400L
