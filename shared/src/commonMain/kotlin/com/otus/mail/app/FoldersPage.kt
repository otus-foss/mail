package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otus.mail.feature.folders.presentation.chipTexts
import com.otus.mail.feature.folders.presentation.components.chipLine
import com.otus.mail.feature.folders.presentation.screens.FoldersScreen

/**
 * Settings › Folders with each folder's rules. Sieve files mail only into the login's own
 * mailboxes, so a mailbox shared with it shows no rules.
 */
@Composable
internal fun FoldersPage(
    states: MailStates,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    LaunchedEffect(Unit) { states.rules.refresh() }
    FoldersScreen(
        states.folders,
        states.folderColors,
        rulesOf = { folder ->
            if (folder.sharedIn != null) {
                emptyList()
            } else {
                states.rules.rules(folder).mapNotNull { it.chipTexts().firstOrNull()?.chipLine() }
            }
        },
        readLater = states.behavior.behavior.readLater,
        onReadLaterChange = { on -> states.behavior.update { it.withReadLater(on) } },
        onBack = back,
    ) { navigation.go(MailPage.FolderDetail(it.accountId, it.id, it.sharedIn)) }
}
