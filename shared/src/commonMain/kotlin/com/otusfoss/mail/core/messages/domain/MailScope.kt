package com.otusfoss.mail.core.messages.domain

import com.otusfoss.mail.core.mailbox.MailboxRole

/**
 * A mailbox of [account], or of every account when null. A [role] finds the folder with that role,
 * whatever it is named; without one, [name] finds the user's folders of that name.
 */
data class MailboxScope(
    val account: String?,
    val name: String,
    val role: MailboxRole? = null,
) {
    fun matches(location: MailLocation): Boolean =
        holds(location.mailbox, location.mailboxRole) && (account == null || location.accountId == account)

    /** Whether a mailbox named [mailbox] with [mailboxRole] is this one. */
    fun holds(
        mailbox: String,
        mailboxRole: MailboxRole?,
    ): Boolean = if (role != null) mailboxRole == role else mailboxRole == null && mailbox == name

    companion object {
        /** Every account's inbox, the default search scope. */
        val Inbox = MailboxScope(null, MailboxRole.Inbox.title, MailboxRole.Inbox)
    }
}

/**
 * Which messages to load: all or named mailboxes of every account, one folder of one account, or
 * several such folders.
 */
sealed interface MailScope {
    /** Every account's folder with [role]; its messages are filed under the role's title. */
    data class Role(
        val role: MailboxRole,
    ) : MailScope

    /**
     * Every account's folders of several roles, such as the inbox with Spam and Trash. Their
     * messages are filed under each role's title.
     */
    data class Roles(
        val roles: List<MailboxRole>,
        /** Also every account's own folders, which have no role, under their own names. */
        val ownFolders: Boolean = false,
    ) : MailScope

    data object AllMailboxes : MailScope

    /** The chosen mailboxes of [accounts]. Empty choices load every mailbox or account. */
    data class Mailboxes(
        val mailboxes: Set<MailboxScope>,
        val accounts: Set<String> = emptySet(),
    ) : MailScope

    /** [accountId] is the login; [folderId] is the server's mailbox id. */
    data class Folder(
        val accountId: String,
        val folderId: String,
    ) : MailScope

    /** Folders of one or more accounts, such as each account's Read later folder. */
    data class Folders(
        val folders: Set<Folder>,
    ) : MailScope

    companion object {
        /** Every account's inbox. */
        val Inbox: MailScope = Role(MailboxRole.Inbox)
    }
}
