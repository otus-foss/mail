package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.mailbox.MailboxRole
import kotlin.time.Instant

/** An account that could not be loaded; [syncedAt] is when its cached mail last matched the server. */
data class UnavailableAccount(
    val name: String,
    val reason: MailFailure,
    val syncedAt: Instant? = null,
)

/** Messages from every connected account, and the accounts that could not be loaded. */
data class InboxSnapshot(
    val messages: List<MailMessage>,
    val unavailable: List<UnavailableAccount> = emptyList(),
    /** Whether older pages remain on the server, which [MailSource.more] loads. */
    val hasMore: Boolean = false,
)

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

interface MailSource {
    suspend fun messages(scope: MailScope): InboxSnapshot

    /** [scope]'s messages that [query] keeps, in its order. Sources without a server ignore the query. */
    suspend fun messages(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = messages(scope)

    /**
     * [scope]'s messages as the device kept them from earlier loads, without asking the server, so
     * a page shows at once. Empty when nothing was kept.
     */
    suspend fun cached(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = InboxSnapshot(emptyList())

    /** The messages [messages] loaded, and the next page of each mailbox that has more. */
    suspend fun more(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = messages(scope, query)

    /** How many messages of [scope] [query] keeps; null when the source cannot count them. */
    suspend fun count(
        scope: MailScope,
        query: MailQuery,
    ): Long? = null
}

/** Local storage failed. Existing inbox data should be retained until the user retries. */
class InboxStorageException(
    cause: Throwable,
    val storage: InboxStorage = InboxStorage.Accounts,
) : Exception("Inbox storage is unavailable.", cause)

enum class InboxStorage { Accounts, Cache }
