package com.otus.mail.feature.inbox.domain

enum class InboxFailure { SignInRejected, NotPermitted, CredentialsUnavailable, NoMail, Unreachable, ServerProblem }

data class UnavailableAccount(
    val name: String,
    val reason: InboxFailure,
)

/** Messages from every connected account, and the accounts that could not be loaded. */
data class InboxSnapshot(
    val messages: List<MailMessage>,
    val unavailable: List<UnavailableAccount> = emptyList(),
)

/**
 * Which messages to load: all or named mailboxes of every account, one folder of one account, or
 * several such folders.
 */
sealed interface MailScope {
    data class Role(
        val role: String,
        val name: String,
    ) : MailScope

    /**
     * Every account's folders of several roles, such as the inbox with Spam and Trash. [names]
     * maps each role to the name its messages are filed under.
     */
    data class Roles(
        val names: Map<String, String>,
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
        val Inbox: MailScope = Role("inbox", "Inbox")
    }
}

interface MailSource {
    suspend fun messages(scope: MailScope): InboxSnapshot

    /** [scope]'s messages that [query] keeps, in its order. Sources without a server ignore the query. */
    suspend fun messages(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = messages(scope)

    /**
     * Starts a run of [count] calls, such as one filter panel session: the counts until the next
     * call may reuse what they read about each account's folders.
     */
    fun beginCounting() {}

    /** How many messages of [scope] [query] keeps; null when the source cannot count them. */
    suspend fun count(
        scope: MailScope,
        query: MailQuery,
    ): Long? = null
}

/** Saved account metadata could not be read. Existing inbox data should be retained. */
class InboxStorageException(
    cause: Throwable,
) : Exception("Inbox account storage is unavailable.", cause)

/** Turns message marks on or off on the server. */
interface MessageMarker {
    /**
     * Sets [flag] to [on] for [messages] together; returns the [MailMessage.id]s it changed. The
     * others were refused or could not be reached.
     */
    suspend fun mark(
        messages: List<MailMessage>,
        flag: MessageFlag,
        on: Boolean,
    ): Set<String>
}

/** Files messages into folders on the server. False when the server refused or could not be reached. */
interface MessageMover {
    /**
     * Moves [message] into [folderId] of the same account and out of [leaving]. It stays in any
     * other mailbox it is in.
     */
    suspend fun move(
        message: MailMessage,
        folderId: String,
        leaving: Set<String>,
    ): Boolean

    /**
     * Undoes a [move] of [message] into [folderId]: puts it back in [returning], the mailboxes the
     * move took it out of, and takes it out of [folderId] unless it was already there before.
     */
    suspend fun moveBack(
        message: MailMessage,
        folderId: String,
        returning: Set<String>,
    ): Boolean

    /** Adds [message] to [folderId], or takes it out, and leaves its other mailboxes as they are. */
    suspend fun setInFolder(
        message: MailMessage,
        folderId: String,
        inFolder: Boolean,
    ): Boolean
}
