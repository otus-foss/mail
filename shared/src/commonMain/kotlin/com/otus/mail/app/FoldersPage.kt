package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otus.mail.feature.folders.presentation.chipTexts
import com.otus.mail.feature.folders.presentation.components.chipLine
import com.otus.mail.feature.folders.presentation.screens.FoldersScreen

/**
 * Settings › Folders & rules with each folder's rules. Sieve files mail only into the login's own
 * mailboxes, so a mailbox shared with it shows no rules.
 */
@Composable
internal fun FoldersPage(
    states: MailStates,
    navigation: MailNavigation,
    back: () -> Unit,
) {
    LaunchedEffect(Unit) { states.folders.rules.refresh() }
    FoldersScreen(
        states.folders.all,
        states.folders.colors,
        states.folders.rules,
        known = states::knownAddresses,
        rulesOf = { folder ->
            if (folder.sharedIn != null) {
                emptyList()
            } else {
                states.folders.rules
                    .rules(folder)
                    .mapNotNull { it.chipTexts().firstOrNull()?.chipLine() }
            }
        },
        onBack = back,
    ) { navigation.go(MailPage.FolderDetail(it.accountId, it.id, it.sharedIn)) }
}
