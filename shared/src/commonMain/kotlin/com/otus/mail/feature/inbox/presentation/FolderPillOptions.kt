package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.mail.folders.elements.folderSymbol
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.FolderNode
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.folderTree
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.MailScope

/**
 * The folder pills [accounts] can fill, in the order Settings lists them. Read later, which the
 * accounts mark only while it is on, and each role the servers report cover every account that has
 * the folder; the inbox is left out, because the page already shows it. Then each account's own
 * folders, under its name.
 */
internal fun folderPillOptions(accounts: List<AccountFolders>): List<FolderPillOption> {
    val trees = accounts.associateWith { it.folderTree() }
    val everyFolder = trees.values.flatten()
    val readLater = everyFolder.filter { it.readLater }
    val roles =
        everyFolder
            .filter { it.folder.role != null && it.folder.role != MailboxRole.Inbox && !it.readLater }
            .groupBy { it.folder.role }
    return buildList {
        if (readLater.isNotEmpty()) add(everyAccount(InboxPill.ReadLater, readLater, "Read later"))
        roles.forEach { (role, nodes) ->
            if (role != null) add(everyAccount(InboxPill.Role(role), nodes, role.title))
        }
        trees.forEach { (account, nodes) ->
            nodes.filter { it.folder.role == null && !it.readLater }.forEach { add(ownFolder(account, it)) }
        }
    }
}

/** Accounts name the same kind of folder differently; one shared name is used, or else [fallback]. */
private fun everyAccount(
    pill: InboxPill.FolderPill,
    nodes: List<FolderNode>,
    fallback: String,
): FolderPillOption =
    FolderPillOption(
        pill,
        nodes.map { it.folder.name }.distinct().singleOrNull() ?: fallback,
        nodes.first().let { folderSymbol(it.folder.role?.wire, it.readLater) },
        MailScope.Folders(nodes.map { MailScope.Folder(it.folder.accountId, it.folder.id) }.toSet()),
        group = EVERY_ACCOUNT_GROUP,
    )

private fun ownFolder(
    account: AccountFolders,
    node: FolderNode,
): FolderPillOption =
    FolderPillOption(
        InboxPill.Folder(account.accountId, node.folder.id),
        node.folder.name,
        folderSymbol(node.folder.role?.wire, node.readLater),
        MailScope.Folder(account.accountId, node.folder.id),
        group = account.accountName,
        path = node.path,
    )

private const val EVERY_ACCOUNT_GROUP = "Folders"
