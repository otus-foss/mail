package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.settings.domain.InboxPill
import com.otus.mail.core.settings.presentation.icon
import com.otus.mail.core.settings.presentation.label
import org.jetbrains.compose.resources.DrawableResource

/**
 * A folder pill the accounts' folders can fill: its [label] and [icon], the folders it loads as
 * [scope], and the [group] and [path] Settings lists it under. App wiring finds these, because the
 * inbox feature does not know folders.
 */
data class FolderPillOption(
    val pill: InboxPill.FolderPill,
    val label: String,
    val icon: DrawableResource,
    val scope: MailScope,
    val group: String,
    val path: List<String> = listOf(label),
) {
    fun scopeFor(accounts: Set<String>): MailScope =
        if (accounts.isEmpty()) {
            scope
        } else {
            when (scope) {
                is MailScope.Folder -> MailScope.Folders(setOf(scope).filter { it.accountId in accounts }.toSet())
                is MailScope.Folders -> scope.copy(folders = scope.folders.filter { it.accountId in accounts }.toSet())
                else -> scope
            }
        }
}

/** The pills a page's row shows, in order. Folder pills appear only with an option in [folders]. */
class InboxPills(
    val pills: List<InboxPill> = InboxPill.defaults(readLater = false),
    val folders: List<FolderPillOption> = emptyList(),
    val foldersLoaded: Boolean = true,
) {
    /** The pills the row shows: every filter, and each folder pill with an option. */
    val shown: List<InboxPill>
        get() = pills.filter { pill -> pill is InboxPill.Filter || folders.any { it.pill == pill } }

    fun option(pill: InboxPill.FolderPill?): FolderPillOption? =
        pill?.takeIf { it in pills }?.let { wanted -> folders.firstOrNull { it.pill == wanted } }
}
