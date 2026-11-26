package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otusfoss.mail.core.messages.domain.FolderRef
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.core.settings.presentation.FolderPillOption
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.domain.scopeToLoad
import com.otusfoss.mail.feature.inbox.domain.searchScope
import com.otusfoss.mail.feature.inbox.presentation.InboxContext
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState
import com.otusfoss.mail.feature.inbox.presentation.InboxState

/**
 * [context] showing the selected folder pill and named after it. This loads the pill's folders, or
 * what a search looks in, and the page's own mail again once neither applies. The pill is dropped
 * once the user takes it off the row.
 */
@Composable
internal fun shownPage(
    state: InboxState,
    context: InboxContext,
    filters: InboxFilterState,
): InboxContext {
    val shown = context.pills.option(state.folderPill)
    val searchFolders = shown?.scope?.let(::pillFolderRefs) ?: context.searchFolders
    val scope = resolvedScope(state, context, filters.applied, shown, searchFolders)
    LaunchedEffect(scope) { context.mailboxSearch.load(scope) }
    DropUnavailablePill(state, context, shown)
    return context.withFolderPill(shown, searchFolders).withSearchFoldersOption(state.searching)
}

@Composable
private fun DropUnavailablePill(
    state: InboxState,
    context: InboxContext,
    shown: FolderPillOption?,
) {
    val pills = context.pills
    LaunchedEffect(pills.pills, pills.folders, pills.foldersLoaded) {
        val unavailable = pills.foldersLoaded && shown == null
        val selected = state.folderPill
        if (selected != null && (selected !in pills.pills || unavailable)) {
            state.showFolder(null)
        }
    }
}

private fun resolvedScope(
    state: InboxState,
    context: InboxContext,
    applied: InboxFilters,
    shown: FolderPillOption?,
    searchFolders: Set<FolderRef>,
): MailScope? =
    when {
        !state.searching -> shown?.scopeFor(state.search.accounts) ?: state.search.scopeToLoad(context.defaultSearch)
        shown != null && !applied.hasFolderOverride -> shown.scopeFor(state.search.accounts)
        else -> state.search.searchScope(searchFolders, applied)
    }

private fun InboxContext.withFolderPill(
    shown: FolderPillOption?,
    searchFolders: Set<FolderRef>,
): InboxContext =
    shown?.let {
        copy(
            title = it.label,
            emptyTitle = "${it.label} is empty",
            searchPrompt = "Search ${it.label}",
            folderPill = it,
            searchFolders = searchFolders,
        )
    } ?: this

/** A search offers the folder picker on every page, with Spam and Trash to add. */
private fun InboxContext.withSearchFoldersOption(searching: Boolean): InboxContext =
    if (searching && filterOptions.accounts.isNotEmpty()) {
        copy(filterOptions = filterOptions.copy(folders = true, bins = true))
    } else {
        this
    }

private fun pillFolderRefs(scope: MailScope): Set<FolderRef> =
    when (scope) {
        is MailScope.Folder -> setOf(FolderRef(scope.accountId, scope.folderId, includeSubfolders = false))
        is MailScope.Folders -> scope.folders.map(::folderRef).toSet()
        else -> emptySet()
    }

private fun folderRef(folder: MailScope.Folder): FolderRef =
    FolderRef(folder.accountId, folder.folderId, includeSubfolders = false)
