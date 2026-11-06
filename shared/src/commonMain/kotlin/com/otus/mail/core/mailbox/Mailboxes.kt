package com.otus.mail.core.mailbox

import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapRequestBuilder
import com.otus.jmap.JmapResponse
import com.otus.mail.core.cache.CacheStorageException
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.cache.withAccount
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailboxObject

/**
 * Each login's own mailboxes as folders. The cache keeps them with the `Mailbox` state they match,
 * so every load after the first asks only `Mailbox/changes` (RFC 8620, section 5.2), and features
 * read them without asking the server. Mailboxes other users share are not kept.
 */
class Mailboxes(
    private val cache: MailCache,
) {
    /** The login's folders as the device kept them; empty before the first load. */
    internal suspend fun cached(loginId: String): List<MailFolder> =
        cache.withAccount(loginId) { accountCache -> accountCache.mailboxes(loginId).map { it.toFolder(loginId) } }

    /** The kept folders, or the server's when the device has none it can read. */
    internal suspend fun of(account: MailAccount): List<MailFolder> =
        keptOrNull { cached(account.login.id) }
            .orEmpty()
            .ifEmpty { sync(account) }

    /**
     * The server's folders now, read as changes since the kept ones where the server can say them,
     * and kept with their state.
     */
    internal suspend fun sync(account: MailAccount): List<MailFolder> {
        val loginId = account.login.id
        val saved = cache.withAccount(loginId) { keptOrNull { it.savedMailboxes(loginId) } }
        return keep(account, account.connection.mailboxList(account.id, saved))
    }

    /**
     * Sends [calls] to [account] and gives the folders with the response: the kept ones, or, when
     * the device has none, ones a `Mailbox/get` in the same request reads, which are kept.
     */
    internal suspend fun send(
        account: MailAccount,
        calls: JmapRequestBuilder.() -> Unit,
    ): Pair<List<MailFolder>, JmapResponse> {
        val kept = keptOrNull { cached(account.login.id) }.orEmpty()
        var mailboxes: MailboxCalls? = null
        val response =
            account.connection.send {
                if (kept.isEmpty()) mailboxes = mailboxCalls(account.id, saved = null)
                calls()
            }
        val read = mailboxes?.let { it.read(response) ?: account.connection.allMailboxes(account.id) }
        return (read?.let { keep(account, it) } ?: kept) to response
    }

    private suspend fun keep(
        account: MailAccount,
        list: MailboxList,
    ): List<MailFolder> {
        val loginId = account.login.id
        cache.withAccount(
            loginId,
        ) { accountCache -> keptOrNull { accountCache.write(loginId) { saveMailboxes(list) } } }
        return list.mailboxes.map { it.toFolder(loginId) }
    }
}

/** [use]'s result, or null when the device's copy cannot be read or saved; the server's folders stay usable. */
private suspend fun <T> keptOrNull(use: suspend () -> T): T? =
    try {
        use()
    } catch (expected: CacheStorageException) {
        null
    }

private suspend fun JmapConnection.mailboxList(
    accountId: String,
    saved: MailboxList?,
): MailboxList {
    lateinit var calls: MailboxCalls
    val response = send { calls = mailboxCalls(accountId, saved) }
    return calls.read(response) ?: allMailboxes(accountId)
}

/** [loginId]'s folder for this mailbox; [sharedIn] names the account another user shares it from. */
internal fun MailboxObject.toFolder(
    loginId: String,
    sharedIn: String? = null,
) = MailFolder(
    accountId = loginId,
    id = id,
    name = name,
    parentId = parentId,
    role = MailboxRole.of(role),
    sortOrder = sortOrder,
    unread = unreadEmails,
    total = totalEmails,
    rights =
        FolderRights(
            mayAddItems = myRights.mayAddItems,
            mayCreateChild = myRights.mayCreateChild,
            mayRename = myRights.mayRename,
            mayDelete = myRights.mayDelete,
            mayRemoveItems = myRights.mayRemoveItems,
            maySetSeen = myRights.maySetSeen,
            maySetKeywords = myRights.maySetKeywords,
        ),
    sharedIn = sharedIn,
    otherRole = role?.lowercase()?.takeIf { MailboxRole.of(it) == null },
)
