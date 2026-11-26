package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.ancestors
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.core.messages.domain.QuickFacet
import com.otusfoss.mail.feature.inbox.domain.MailQuery

/** Whether this scope reads mail of the login with [loginId]. */
internal fun MailScope.reads(loginId: String): Boolean =
    when (this) {
        MailScope.AllMail, MailScope.AllMailboxes, is MailScope.Role, is MailScope.Roles -> true
        is MailScope.Mailboxes -> includesLogin(loginId)
        is MailScope.Folder -> accountId == loginId
        is MailScope.Folders -> folders.any { it.accountId == loginId }
    }

private fun MailScope.Mailboxes.includesLogin(loginId: String): Boolean =
    (accounts.isEmpty() || loginId in accounts) &&
        (mailboxes.isEmpty() || mailboxes.any { it.account == null || it.account == loginId })

/**
 * The mailboxes of the login [loginId] that this scope loads, among its [mailboxes]. Folders
 * chosen in [query] replace the scope's own; included Spam or Trash roles add those folders when
 * no specific folders are picked. Awaiting reply adds the Sent mailbox to the scope's own, because
 * a thread the user started may have no email anywhere else.
 */
internal fun MailScope.chosen(
    mailboxes: List<MailFolder>,
    loginId: String,
    query: MailQuery,
): List<MailFolder> {
    val ids = query.folderIds[loginId]
    val sent = mailboxes.filter { it.role == MailboxRole.Sent }
    val bins = mailboxes.filter { it.inIncludedBin(mailboxes, query.filters.includedBins) }
    return when {
        ids != null -> mailboxes.filter { it.id in ids }
        query.filters.folders.isNotEmpty() -> emptyList()
        query.filters.has(QuickFacet.AwaitingReply) -> (scoped(mailboxes, loginId) + bins + sent).distinctBy { it.id }
        bins.isNotEmpty() -> (scoped(mailboxes, loginId) + bins).distinctBy { it.id }
        else -> scoped(mailboxes, loginId)
    }
}

private fun MailScope.scoped(
    mailboxes: List<MailFolder>,
    loginId: String,
): List<MailFolder> =
    when (this) {
        is MailScope.Role -> mailboxes.filter { it.role == role }
        is MailScope.Roles -> mailboxes.filter { it.role in roles || (ownFolders && it.isCustom) }
        MailScope.AllMail -> mailboxes.filterNot { it.inBin(mailboxes) }
        MailScope.AllMailboxes -> mailboxes
        is MailScope.Mailboxes -> mailboxes.filter { includesMailbox(it, loginId, mailboxes) }
        is MailScope.Folder -> mailboxes.filter { it.id == folderId }
        is MailScope.Folders -> mailboxes.filter { MailScope.Folder(loginId, it.id) in folders }
    }

private fun MailScope.Mailboxes.includesMailbox(
    mailbox: MailFolder,
    loginId: String,
    all: List<MailFolder>,
): Boolean =
    if (mailboxes.isEmpty()) {
        includeBins || !mailbox.inBin(all)
    } else {
        mailboxes.any {
            it.holds(mailbox.mailboxName, mailbox.role) && (it.account == null || it.account == loginId)
        }
    }

private fun MailFolder.inBin(mailboxes: List<MailFolder>): Boolean =
    (listOf(this) + ancestors(mailboxes, this)).any { it.isBin }

private fun MailFolder.inIncludedBin(
    mailboxes: List<MailFolder>,
    includedBins: Set<MailboxRole>,
): Boolean =
    includedBins.isNotEmpty() &&
        (listOf(this) + ancestors(mailboxes, this)).any { it.role in includedBins }

/** The name [mailbox]'s messages are filed under in this scope: a role scope's title for its roles. */
internal fun MailScope.nameOf(mailbox: MailFolder): String =
    mailbox.role
        ?.takeIf { (this is MailScope.Role && it == role) || (this is MailScope.Roles && it in roles) }
        ?.title
        ?: mailbox.mailboxName
