package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.presentation.FolderPillOption

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
            folders.map { OrderedChoice(it.pill, it.pill.storageValue, it.path, it.icon, it.group) }
    OrderedChoiceSheet("Pills", selected, choices, InboxPill::storageValue, onApply, onDismiss)
}
