package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.selection.components.OrderedChoice
import com.otus.designsystem.shared.selection.components.OrderedChoiceSheet
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.nestedByPath

/** Chooses the inbox row's pills: the filters, then each group of [folders] pills. */
@Composable
internal fun InboxPillsSheet(
    selected: List<InboxPill>,
    folders: List<FolderPillOption>,
    onApply: (List<InboxPill>) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices =
        InboxPill.Filter.entries.map { OrderedChoice(it, it.storageValue, it.label, it.icon, "Filters") } +
            folders
                .groupBy { it.group }
                .values
                .flatMap { group -> group.nestedByPath { it.path } }
                .map { (folder, name, depth) ->
                    OrderedChoice(
                        folder.pill,
                        folder.pill.storageValue,
                        folder.path,
                        folder.icon,
                        folder.group,
                        name,
                        depth,
                    )
                }
    OrderedChoiceSheet("Pills", selected, choices, InboxPill::storageValue, onApply, onDismiss)
}
