package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otus.accounts.domain.ownAddresses
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.feature.folders.domain.FolderKey
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.key
import com.otus.mail.feature.folders.presentation.NUDGE_COLORS
import com.otus.mail.feature.inbox.domain.FolderKind
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.OwnAddresses
import com.otus.mail.feature.inbox.presentation.ListNudge
import com.otus.mail.feature.inbox.presentation.RowLook

/** The Mail list settings and every account's sign-in and sending addresses and their avatars, for the rows. */
@Composable
internal fun rememberRowLook(states: MailStates): RowLook {
    val layout = states.behavior.behavior.list
    val accounts = states.accounts.accounts
    LaunchedEffect(accounts) { states.ownAvatars.load(accounts.map { it.own }) }
    val avatars =
        accounts
            .flatMap { account ->
                account.ownAddresses.mapNotNull { email ->
                    states.ownAvatars.avatar(account.own, email)?.let { (account.id to email.lowercase()) to it }
                }
            }.toMap()
    return remember(layout, accounts, avatars) {
        RowLook(layout, OwnAddresses(accounts.associate { it.id to it.emailAddresses + it.username }), avatars)
    }
}

/**
 * The folder color nudge for the folder among [messages]' rows that most needs a color, or null.
 * Showing it records the folder, so it never comes back after this launch.
 */
@Composable
internal fun rememberListNudge(
    states: MailStates,
    messages: List<MailMessage>,
): ListNudge? {
    val visible = remember(messages) { messages.folderCounts() }
    val folder = states.folderNudge.pick(visible)?.folder ?: return null
    LaunchedEffect(folder.key) { states.folderNudge.markShown(folder.key) }
    return ListNudge(
        folder.name,
        NUDGE_COLORS,
        onPick = { color -> states.colorFolder(folder, color) },
        onDismiss = { states.folderNudge.dismiss(folder.key) },
    )
}

/** How many of [this] conversations show each of the user's own folders. */
private fun List<MailMessage>.folderCounts(): Map<FolderKey, Int> =
    flatMap { message ->
        message.labels
            .filter { it.kind == FolderKind.Folder && !it.readLater }
            .map { FolderKey(message.location.accountId, it.id) }
    }.groupingBy { it }.eachCount()

/** Gives [folder] [color], with a toast whose Undo takes the color away again. */
private fun MailStates.colorFolder(
    folder: MailFolder,
    color: FolderColor,
) {
    folderColors.setColor(listOf(folder), color.name)
    val undo = UiAction("Undo") { folderColors.setColor(listOf(folder), null) }
    moveToasts.toasts.show(ToastMessage("${folder.name} is now ${color.label.lowercase()}", undo))
}
