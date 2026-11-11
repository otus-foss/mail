package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.selection.components.OrderedChoice
import com.otusfoss.core.designsystem.shared.selection.components.OrderedChoiceSheet
import com.otusfoss.mail.core.mailbox.pathText
import com.otusfoss.mail.core.settings.domain.InboxPill
import com.otusfoss.mail.core.settings.presentation.FolderPillOption
import com.otusfoss.mail.core.settings.presentation.icon
import com.otusfoss.mail.core.settings.presentation.label

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
            folders.map { folder ->
                OrderedChoice(
                    folder.pill,
                    folder.pill.storageValue,
                    folder.path.pathText(),
                    folder.icon,
                    folder.group,
                    folder.path,
                )
            }
    OrderedChoiceSheet("Pills", selected, choices, InboxPill::storageValue, onApply, onDismiss)
}
