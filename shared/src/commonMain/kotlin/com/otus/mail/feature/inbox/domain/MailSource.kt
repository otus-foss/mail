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

/** Which messages to load: every account's inbox, or one folder of one account. */
sealed interface MailScope {
    data object Inbox : MailScope

    /** [accountId] is the login; [folderId] is the server's mailbox id. */
    data class Folder(
        val accountId: String,
        val folderId: String,
    ) : MailScope
}

interface MailSource {
    suspend fun messages(scope: MailScope): InboxSnapshot
}

/** Saved account metadata could not be read. Existing inbox data should be retained. */
class InboxStorageException(
    cause: Throwable,
) : Exception("Inbox account storage is unavailable.", cause)

/** Turns message marks on or off on the server. */
interface MessageMarker {
    /** Sets [flag] to [on] for [message]; false when the server refused or could not be reached. */
    suspend fun mark(
        message: MailMessage,
        flag: MessageFlag,
        on: Boolean,
    ): Boolean
}

/** Files messages into folders on the server. */
interface MessageMover {
    /** Moves [message] from its mailbox into [folderId] of the same account; false when the server refused. */
    suspend fun move(
        message: MailMessage,
        folderId: String,
    ): Boolean
}
