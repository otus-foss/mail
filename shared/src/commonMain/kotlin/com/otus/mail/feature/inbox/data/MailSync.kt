package com.otus.mail.feature.inbox.data

import com.otus.jmap.ChangesMethod
import com.otus.jmap.ChangesResult
import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.JmapResponse
import com.otus.jmap.JmapType
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.CacheWrite
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MAILBOX_PROPERTIES
import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.core.mail.ThreadType
import com.otus.mail.core.mail.replaceMailboxes

/**
 * What changed in one type's objects since the saved state (RFC 8620, section 5.2). [changed]
 * holds created and updated ids. With [reset], the cached objects of the type can no longer be
 * patched and are dropped: there was no saved state, the server could not calculate the changes,
 * or there were too many of them. [state] is the one to save, null when the server gave none.
 */
internal data class ObjectChanges(
    val state: String?,
    val changed: Set<String> = emptySet(),
    val destroyed: Set<String> = emptySet(),
    val reset: Boolean = false,
) {
    fun then(page: ChangesResult): ObjectChanges =
        copy(
            state = page.newState,
            changed = changed + page.created + page.updated - page.destroyed.toSet(),
            destroyed = destroyed + page.destroyed,
        )

    /** Leaves [kinds] as the server has them now, short of the objects fetched again. */
    fun applyTo(
        write: CacheWrite,
        vararg kinds: CacheKind,
    ) {
        kinds.forEach { kind -> if (reset) write.clear(kind) else write.remove(kind, changed + destroyed) }
    }

    companion object {
        /** Nothing changed, for loads that only add pages. */
        val NONE = ObjectChanges(state = null)
    }
}

/** A login's mailboxes and what changed in its emails and threads, as one request found them. */
internal class MailChanges(
    val mailboxes: List<MailboxObject>,
    val emails: ObjectChanges,
    val threads: ObjectChanges,
)

/**
 * The mailboxes and the `Email/changes` and `Thread/changes` since the login's saved states, in
 * one request. Empty `/get` calls give the current states, used when there is nothing to start
 * from. Further pages of changes take a request each, up to [MAX_PAGES].
 */
internal suspend fun JmapConnection.mailChanges(
    accountId: String,
    cache: MailCache,
    loginId: String,
): MailChanges {
    val emailState = cache.value(loginId, EMAIL_STATE)
    val threadState = cache.value(loginId, THREAD_STATE)
    lateinit var mailboxes: JmapCall<GetResult<MailboxObject>>
    lateinit var emailsNow: JmapCall<GetResult<*>>
    lateinit var threadsNow: JmapCall<GetResult<*>>
    var emailChanges: JmapCall<ChangesResult>? = null
    var threadChanges: JmapCall<ChangesResult>? = null
    val response =
        send {
            mailboxes = call(GetMethod(MailboxType, accountId, properties = MAILBOX_PROPERTIES))
            emailsNow = call(GetMethod(EmailType, accountId, JmapIds.Of(emptyList()), listOf("id")))
            threadsNow = call(GetMethod(ThreadType, accountId, JmapIds.Of(emptyList())))
            emailChanges = emailState?.let { call(ChangesMethod(EmailType, accountId, it, MAX_CHANGES)) }
            threadChanges = threadState?.let { call(ChangesMethod(ThreadType, accountId, it, MAX_CHANGES)) }
        }
    return MailChanges(
        mailboxes = response[mailboxes].list,
        emails = objectChanges(EmailType, accountId, response, emailsNow, emailChanges),
        threads = objectChanges(ThreadType, accountId, response, threadsNow, threadChanges),
    )
}

/** Saves [changes]: the mailboxes, the removal of changed and destroyed objects, and the states. */
internal fun CacheWrite.saveChanges(changes: MailChanges) {
    replaceMailboxes(changes.mailboxes)
    changes.emails.applyTo(this, CacheKind.Email, CacheKind.Member)
    changes.threads.applyTo(this, CacheKind.Thread)
    putValue(EMAIL_STATE, changes.emails.state)
    putValue(THREAD_STATE, changes.threads.state)
}

private suspend fun JmapConnection.objectChanges(
    type: JmapType<*>,
    accountId: String,
    response: JmapResponse,
    now: JmapCall<GetResult<*>>,
    first: JmapCall<ChangesResult>?,
): ObjectChanges =
    first
        ?.let { call -> methodResult { response[call] } }
        ?.let { page -> followChanges(type, accountId, page) }
        ?: ObjectChanges(state = methodResult { response[now] }?.state?.ifEmpty { null }, reset = true)

/** Every page of changes from [first] on; null when the server stops answering or has too many. */
private suspend fun JmapConnection.followChanges(
    type: JmapType<*>,
    accountId: String,
    first: ChangesResult,
): ObjectChanges? {
    var page = first
    var changes = ObjectChanges(state = null).then(page)
    var pages = 1
    while (page.hasMoreChanges && pages < MAX_PAGES) {
        page = methodResult { call(ChangesMethod(type, accountId, page.newState, MAX_CHANGES)) } ?: return null
        changes = changes.then(page)
        pages++
    }
    return changes.takeUnless { page.hasMoreChanges }
}

/**
 * [read]'s result, or null when the server refused that call. Any refusal of a `/changes` call,
 * `cannotCalculateChanges` or not, means the cache cannot be patched, so it starts over.
 */
private inline fun <T> methodResult(read: () -> T): T? =
    try {
        read()
    } catch (expected: JmapException.MethodError) {
        null
    }

/** Saved-value keys of the states the cached emails and threads match. */
private const val EMAIL_STATE = "state:Email"
private const val THREAD_STATE = "state:Thread"

/** Changes asked for per call; the server may send fewer (RFC 8620, section 5.2). */
private const val MAX_CHANGES = 500

/** After this many pages, fetching the lists again costs less than reading more changes. */
private const val MAX_PAGES = 4
