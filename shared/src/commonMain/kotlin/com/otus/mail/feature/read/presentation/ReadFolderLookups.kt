package com.otus.mail.feature.read.presentation

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.folderTree
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MessageFolder
import com.otus.mail.feature.read.presentation.components.NewFolderSheet

/**
 * The folders of the Read view of messages a list of [scope], as it is now, shows: each account's folders from
 * [account], with the colors [colorOf] names, and the folders the list shows each message from.
 */
fun readFolders(
    account: (accountId: String) -> AccountFolders?,
    scope: () -> MailScope,
    colorOf: (MailFolder) -> String?,
    newFolder: NewFolderSheet?,
): ReadFolders =
    ReadFolders(
        of = { message -> account(message.location.accountId)?.messageFolders(colorOf).orEmpty() },
        cameFrom = { message -> scope().cameFrom(message, account(message.location.accountId)?.folders.orEmpty()) },
        newFolder = newFolder,
    )

/**
 * The folders of [message]'s account, [own], that a list of [this] scope shows it from: a folder
 * page's folder, or the account's folders of the roles a page such as the unified inbox shows,
 * with its own folders when the page shows them too. Empty for lists without a folder, such as
 * All Mail.
 */
internal fun MailScope.cameFrom(
    message: MailMessage,
    own: List<MailFolder>,
): Set<String> {
    val accountId = message.location.accountId
    return when (this) {
        is MailScope.Folder -> if (this.accountId == accountId) setOf(folderId) else emptySet()
        is MailScope.Folders -> folders.filter { it.accountId == accountId }.map { it.folderId }.toSet()
        is MailScope.Role -> own.filter { it.role == role }.map { it.id }.toSet()
        is MailScope.Roles -> own.filter { it.role in roles || (ownFolders && it.isCustom) }.map { it.id }.toSet()
        else -> emptySet()
    }
}

/** The account's folders in tree order, each with its full path and saved color. */
private fun AccountFolders.messageFolders(colorOf: (MailFolder) -> String?): List<MessageFolder> =
    folderTree().map { node ->
        MessageFolder(node.folder.id, node.path, node.folder.role, colorOf(node.folder), node.readLater)
    }
