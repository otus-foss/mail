package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.FolderNode
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.folderTree
import com.otus.mail.core.mailbox.presentation.READ_LATER_TITLE
import com.otus.mail.core.mailbox.presentation.symbol
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.settings.domain.InboxPill
import com.otus.mail.core.settings.presentation.FolderPillOption
import com.otus.mail.core.settings.presentation.title

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
            .filter { !it.folder.isCustom && it.folder.role != MailboxRole.Inbox && !it.readLater }
            .groupBy { it.folder.role?.wire ?: it.folder.otherRole.orEmpty() }
    return buildList {
        if (readLater.isNotEmpty()) add(everyAccount(InboxPill.ReadLater, readLater, READ_LATER_TITLE))
        roles.forEach { (wire, nodes) ->
            val role = MailboxRole.of(wire)
            val pill = role?.let(InboxPill::Role) ?: InboxPill.OtherRole(wire)
            add(everyAccount(pill, nodes, role?.title ?: wire.replaceFirstChar { it.uppercase() }))
        }
        trees.forEach { (account, nodes) ->
            nodes.filter { it.folder.isCustom && !it.readLater }.forEach { add(ownFolder(account, it)) }
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
        nodes.first().symbol,
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
        node.symbol,
        MailScope.Folder(account.accountId, node.folder.id),
        group = account.accountName,
        path = node.path,
    )

private const val EVERY_ACCOUNT_GROUP = "Folders"
