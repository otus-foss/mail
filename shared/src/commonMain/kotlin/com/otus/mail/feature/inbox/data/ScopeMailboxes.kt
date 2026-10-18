package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.mailboxName

/** Whether this scope reads mail of the login with [loginId]. */
internal fun MailScope.reads(loginId: String): Boolean =
    when (this) {
        MailScope.AllMailboxes, is MailScope.Role, is MailScope.Roles -> {
            true
        }

        is MailScope.Mailboxes -> {
            (accounts.isEmpty() || loginId in accounts) &&
                (mailboxes.isEmpty() || mailboxes.any { it.account == null || it.account == loginId })
        }

        is MailScope.Folder -> {
            accountId == loginId
        }

        is MailScope.Folders -> {
            folders.any { it.accountId == loginId }
        }
    }

/**
 * The mailboxes of the login [loginId] that this scope loads, among its [mailboxes]. Folders
 * chosen in [query] replace the scope's own.
 */
internal fun MailScope.chosen(
    mailboxes: List<MailboxObject>,
    loginId: String,
    query: MailQuery,
): List<MailboxObject> {
    query.folderIds[loginId]?.let { ids -> return mailboxes.filter { it.id in ids } }
    return when (this) {
        is MailScope.Role -> {
            mailboxes.filter { it.role == role }
        }

        is MailScope.Roles -> {
            mailboxes.filter { it.role in names || (ownFolders && it.role == null) }
        }

        MailScope.AllMailboxes -> {
            mailboxes
        }

        is MailScope.Mailboxes -> {
            mailboxes.filter { mailbox ->
                this.mailboxes.isEmpty() ||
                    this.mailboxes.any {
                        it.name == mailboxName(mailbox.name, mailbox.role) &&
                            (it.account == null || it.account == loginId)
                    }
            }
        }

        is MailScope.Folder -> {
            mailboxes.filter { it.id == folderId }
        }

        is MailScope.Folders -> {
            mailboxes.filter { MailScope.Folder(loginId, it.id) in folders }
        }
    }
}

/** The name [mailbox]'s messages are filed under in this scope. */
internal fun MailScope.nameOf(mailbox: MailboxObject): String =
    when (this) {
        is MailScope.Role -> name
        is MailScope.Roles -> names[mailbox.role] ?: mailboxName(mailbox.name, mailbox.role)
        else -> mailboxName(mailbox.name, mailbox.role)
    }
