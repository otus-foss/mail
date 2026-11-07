package com.otus.mail.core.mailbox

import com.otus.jmap.JmapRequestBuilder
import com.otus.jmap.JmapResponse
import com.otus.mail.core.cache.CacheStorageException
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.cache.withAccount
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailboxObject

/**
 * Each login's own mailboxes as folders, and the one keeper of them and the `Mailbox` state they
 * match in the cache. After the first load, bringing them up to date asks only `Mailbox/changes`
 * (RFC 8620, section 5.2). Mailboxes other users share are not kept.
 */
class Mailboxes(
    private val cache: MailCache,
) {
    /** The login's folders as the device kept them; empty before the first load. */
    internal suspend fun cached(loginId: String): List<MailFolder> =
        cache.withAccount(loginId) { accountCache -> accountCache.mailboxes(loginId).map { it.toFolder(loginId) } }

    /**
     * The kept folders, or the server's when the device has none it can read. For reads that can
     * wait for the next sync to see a new or renamed folder, such as counts and suggestions.
     */
    internal suspend fun of(account: MailAccount): List<MailFolder> =
        keptOrNull { cached(account.login.id) }
            .orEmpty()
            .ifEmpty { sync(account) }

    /** The server's folders now, read as changes since the kept ones where the server can say them. */
    internal suspend fun sync(account: MailAccount): List<MailFolder> = sendFresh(account) {}.first

    /**
     * Sends [calls] to [account] and gives the folders with the response: the kept ones, or, when
     * the device has none, ones a `Mailbox/get` in the same request reads, which are kept.
     */
    internal suspend fun send(
        account: MailAccount,
        calls: JmapRequestBuilder.() -> Unit,
    ): Pair<List<MailFolder>, JmapResponse> {
        val kept = keptOrNull { cached(account.login.id) }.orEmpty()
        if (kept.isEmpty()) return request(account, saved = null, reportStorage = false, calls)
        return kept to account.connection.send(calls)
    }

    /**
     * Sends [calls] to [account] with the calls that bring its folders up to date, and gives both.
     * With [reportStorage], a device that cannot keep the folders throws [CacheStorageException],
     * as the inbox reports it; otherwise the server's folders are used without keeping them.
     */
    internal suspend fun sendFresh(
        account: MailAccount,
        reportStorage: Boolean = false,
        calls: JmapRequestBuilder.() -> Unit,
    ): Pair<List<MailFolder>, JmapResponse> {
        val loginId = account.login.id
        val saved = cache.withAccount(loginId) { keptOrNull { it.savedMailboxes(loginId) } }
        return request(account, saved, reportStorage, calls)
    }

    private suspend fun request(
        account: MailAccount,
        saved: MailboxList?,
        reportStorage: Boolean,
        calls: JmapRequestBuilder.() -> Unit,
    ): Pair<List<MailFolder>, JmapResponse> {
        lateinit var mailboxes: MailboxCalls
        val response =
            account.connection.send {
                mailboxes = mailboxCalls(account.id, saved)
                calls()
            }
        val list = mailboxes.read(response) ?: account.connection.allMailboxes(account.id)
        return keep(account, list, reportStorage) to response
    }

    private suspend fun keep(
        account: MailAccount,
        list: MailboxList,
        reportStorage: Boolean,
    ): List<MailFolder> {
        val loginId = account.login.id
        cache.withAccount(loginId) { accountCache ->
            val write = suspend { accountCache.write(loginId) { saveMailboxes(list) } }
            if (reportStorage) write() else keptOrNull(write)
        }
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
