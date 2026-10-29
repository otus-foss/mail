package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.core.mail.SENT_ROLE
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.QuickFacet
import com.otus.mail.feature.inbox.domain.mailboxName

/** Whether this scope reads mail of the login with [loginId]. */
internal fun MailScope.reads(loginId: String): Boolean =
    when (this) {
        MailScope.AllMailboxes, is MailScope.Role, is MailScope.Roles -> true
        is MailScope.Mailboxes -> includesLogin(loginId)
        is MailScope.Folder -> accountId == loginId
        is MailScope.Folders -> folders.any { it.accountId == loginId }
    }

private fun MailScope.Mailboxes.includesLogin(loginId: String): Boolean =
    (accounts.isEmpty() || loginId in accounts) &&
        (mailboxes.isEmpty() || mailboxes.any { it.account == null || it.account == loginId })

/**
 * The mailboxes of the login [loginId] that this scope loads, among its [mailboxes]. Folders
 * chosen in [query] replace the scope's own. Awaiting reply adds the Sent mailbox to the scope's
 * own, because a thread the user started may have no email anywhere else.
 */
internal fun MailScope.chosen(
    mailboxes: List<MailboxObject>,
    loginId: String,
    query: MailQuery,
): List<MailboxObject> {
    val ids = query.folderIds[loginId]
    val sent = mailboxes.filter { it.role == SENT_ROLE }
    return when {
        ids != null -> mailboxes.filter { it.id in ids }
        query.filters.has(QuickFacet.AwaitingReply) -> (scoped(mailboxes, loginId) + sent).distinctBy { it.id }
        else -> scoped(mailboxes, loginId)
    }
}

private fun MailScope.scoped(
    mailboxes: List<MailboxObject>,
    loginId: String,
): List<MailboxObject> =
    when (this) {
        is MailScope.Role -> mailboxes.filter { it.role == role }
        is MailScope.Roles -> mailboxes.filter { it.role in names || (ownFolders && it.role == null) }
        MailScope.AllMailboxes -> mailboxes
        is MailScope.Mailboxes -> mailboxes.filter { includesMailbox(it, loginId) }
        is MailScope.Folder -> mailboxes.filter { it.id == folderId }
        is MailScope.Folders -> mailboxes.filter { MailScope.Folder(loginId, it.id) in folders }
    }

private fun MailScope.Mailboxes.includesMailbox(
    mailbox: MailboxObject,
    loginId: String,
): Boolean =
    mailboxes.isEmpty() ||
        mailboxes.any {
            it.name == mailboxName(mailbox.name, mailbox.role) && (it.account == null || it.account == loginId)
        }

/** The name [mailbox]'s messages are filed under in this scope. */
internal fun MailScope.nameOf(mailbox: MailboxObject): String =
    when (this) {
        is MailScope.Role -> if (mailbox.role == role) name else mailboxName(mailbox.name, mailbox.role)
        is MailScope.Roles -> names[mailbox.role] ?: mailboxName(mailbox.name, mailbox.role)
        else -> mailboxName(mailbox.name, mailbox.role)
    }
