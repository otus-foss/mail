package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
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
