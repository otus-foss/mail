package com.otusfoss.mail.feature.folders.domain

import com.otusfoss.mail.core.mailbox.FolderNode
import com.otusfoss.mail.core.mailbox.MailboxRole

/**
 * The folders of an account's tree that a message in [folderIds] names on its row when shown in
 * [shownId]: the inbox and the user's own folders, Read later among them, in tree order. Sent,
 * Trash, and other system folders are left out, and so is the folder the page already shows.
 */
fun List<FolderNode>.labelFolders(
    folderIds: Set<String>,
    shownId: String,
): List<FolderNode> =
    filter { node ->
        val folder = node.folder
        folder.id in folderIds && folder.id != shownId && (folder.isCustom || folder.role == MailboxRole.Inbox)
    }
