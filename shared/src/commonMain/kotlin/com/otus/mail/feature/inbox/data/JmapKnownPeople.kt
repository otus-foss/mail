package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.NEWEST_FIRST
import com.otus.mail.core.mail.inMailbox
import com.otus.mail.core.mail.mailCallOr
import com.otus.mail.feature.inbox.domain.KnownPeople
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The people the user knows on an account: the addresses on its contact cards (RFC 9610) and the
 * recipients of its newest [SENT_SCAN] sent emails (RFC 8621, section 4.4). Remote images load for
 * them, so a failed load gives none and the next call tries again. Each account's set is kept for
 * [maxAge], since every opened message asks. The lock guards only the kept sets, so a slow server
 * holds up no other account.
 */
class JmapKnownPeople(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val maxAge: Duration = 10.minutes,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : KnownPeople {
    private val lock = Mutex()
    private var known: Map<String, Known> = emptyMap()

    override suspend fun addresses(accountId: String): Set<String> =
        lock.withLock { known[accountId] }?.takeIf { it.loadedAt.elapsedNow() < maxAge }?.addresses
            ?: load(accountId)?.also { lock.withLock { known += accountId to Known(it, timeSource.markNow()) } }
            ?: emptySet()

    private suspend fun load(accountId: String): Set<String>? =
        mailCallOr(null) {
            mailAccount(connections, logins, accountId)?.let { account ->
                (account.contactCards(CONTACT_SCAN).map { it.address } + account.sentTo())
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
        }

    /**
     * Everyone in To, Cc, and Bcc of the newest mail in the Sent folder; none without one. The
     * emails are read in batches the server's `maxObjectsInGet` allows.
     */
    private suspend fun MailAccount.sentTo(): List<String> {
        val sent = connection.call(roleQuery(SENT_ROLE)).ids.firstOrNull() ?: return emptyList()
        val ids = connection.call(QueryMethod(EmailType, id, inMailbox(sent), NEWEST_FIRST, SENT_SCAN)).ids
        return ids
            .chunked(session.coreLimit("maxObjectsInGet", GET_BATCH))
            .flatMap { batch -> connection.call(GetMethod(EmailType, id, JmapIds.Of(batch), PROPERTIES)).list }
            .flatMap { it.to.orEmpty() + it.cc.orEmpty() + it.bcc.orEmpty() }
            .map { it.email }
    }

    private class Known(
        val addresses: Set<String>,
        val loadedAt: TimeMark,
    )

    private companion object {
        const val CONTACT_SCAN = 500
        const val SENT_SCAN = 500

        /** Read when the server names no `maxObjectsInGet`; small enough for any server. */
        const val GET_BATCH = 200
        const val SENT_ROLE = "sent"
        val PROPERTIES = listOf("id", "receivedAt", "to", "cc", "bcc")
    }
}
