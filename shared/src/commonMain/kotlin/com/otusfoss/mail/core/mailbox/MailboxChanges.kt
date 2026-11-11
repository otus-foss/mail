package com.otusfoss.mail.core.mailbox

import com.otusfoss.core.jmap.ChangesMethod
import com.otusfoss.core.jmap.ChangesResult
import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.GetResult
import com.otusfoss.core.jmap.JmapCall
import com.otusfoss.core.jmap.JmapConnection
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.JmapRequestBuilder
import com.otusfoss.core.jmap.JmapResponse
import com.otusfoss.mail.core.cache.CacheKind
import com.otusfoss.mail.core.cache.CacheWrite
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.mail.MAILBOX_PROPERTIES
import com.otusfoss.mail.core.mail.MailboxObject
import com.otusfoss.mail.core.mail.MailboxType
import com.otusfoss.mail.core.mail.allObjects
import com.otusfoss.mail.core.mail.putObject
import com.otusfoss.mail.core.mail.unlessRefused

/** A login's mailboxes and the `Mailbox` state they match; null when the server gave none. */
internal class MailboxList(
    val mailboxes: List<MailboxObject>,
    val state: String?,
)

/**
 * The calls that bring a login's mailboxes up to date inside a larger request. With a saved state
 * and cached mailboxes, `Mailbox/changes` (RFC 8620, section 5.2) and a `Mailbox/get` of what it
 * names patch the cache; otherwise one `Mailbox/get` reads them all.
 */
internal sealed interface MailboxCalls {
    /** The mailboxes the response holds; null when the changes could not patch the cache. */
    fun read(response: JmapResponse): MailboxList?

    class All(
        private val get: JmapCall<GetResult<MailboxObject>>,
    ) : MailboxCalls {
        override fun read(response: JmapResponse) =
            response[get].let { MailboxList(it.list, it.state.ifEmpty { null }) }
    }

    class Changes(
        private val cached: List<MailboxObject>,
        private val changes: JmapCall<ChangesResult>,
        private val created: JmapCall<GetResult<MailboxObject>>,
        private val updated: JmapCall<GetResult<MailboxObject>>,
    ) : MailboxCalls {
        override fun read(response: JmapResponse): MailboxList? {
            val found = unlessRefused { response[changes] }?.takeUnless { it.hasMoreChanges } ?: return null
            val fetched = (response[created].list + response[updated].list).associateBy { it.id }
            val gone = found.destroyed.toSet() + fetched.keys
            return MailboxList(cached.filterNot { it.id in gone } + fetched.values, found.newState)
        }
    }
}

/** The calls for [accountId]'s mailboxes, from the [saved] state and mailboxes where there are any. */
internal fun JmapRequestBuilder.mailboxCalls(
    accountId: String,
    saved: MailboxList?,
): MailboxCalls {
    val state = saved?.state ?: return MailboxCalls.All(call(everyMailbox(accountId)))
    val changes = call(ChangesMethod(MailboxType, accountId, state, MAX_MAILBOX_CHANGES))
    return MailboxCalls.Changes(
        saved.mailboxes,
        changes,
        call(GetMethod(MailboxType, accountId, JmapIds.Result(changes.resultOf("/created")), MAILBOX_PROPERTIES)),
        call(GetMethod(MailboxType, accountId, JmapIds.Result(changes.resultOf("/updated")), MAILBOX_PROPERTIES)),
    )
}

/** The cached mailboxes and the state they match, or null when there is nothing to patch. */
internal suspend fun MailCache.savedMailboxes(loginId: String): MailboxList? {
    val state = value(loginId, MAILBOX_STATE) ?: return null
    return mailboxes(loginId).takeIf { it.isNotEmpty() }?.let { MailboxList(it, state) }
}

/** Every mailbox in a request of its own, when the changes could not patch the cache. */
internal suspend fun JmapConnection.allMailboxes(accountId: String): MailboxList =
    call(everyMailbox(accountId)).let { MailboxList(it.list, it.state.ifEmpty { null }) }

/** Replaces the login's cached mailboxes and the state they match with [list]; only [Mailboxes] keeps them. */
internal fun CacheWrite.saveMailboxes(list: MailboxList) {
    clear(CacheKind.Mailbox)
    list.mailboxes.forEach { putObject(CacheKind.Mailbox, it.id, MailboxObject.serializer(), it) }
    putValue(MAILBOX_STATE, list.state)
}

/** The login's cached mailboxes, as the last load saved them. */
internal suspend fun MailCache.mailboxes(loginId: String): List<MailboxObject> =
    allObjects(loginId, CacheKind.Mailbox, MailboxObject.serializer()).values.toList()

private fun everyMailbox(accountId: String) = GetMethod(MailboxType, accountId, properties = MAILBOX_PROPERTIES)

private const val MAILBOX_STATE = "state:Mailbox"

/** More mailbox changes than this cost more to read than the whole list. */
private const val MAX_MAILBOX_CHANGES = 200
