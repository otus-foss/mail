package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.cache.withAccount
import com.otus.mail.feature.inbox.domain.InboxFailure
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.UnavailableAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Loads all or named mailboxes of each login, or chosen folders, through [cache]: a load brings the
 * cache up to date with the server and then reads the lists from it, so [cached] shows the same
 * rows without the server. Logins load in parallel and fail alone; a failed login still shows what
 * the cache holds. Each list loads [limit] conversations at a time.
 */
class JmapMailSource(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val cache: MailCache = InMemoryMailCache(),
    private val limit: Int = DEFAULT_LIMIT,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val clock: Clock = Clock.System,
) : MailSource {
    // A refresh and a next page of one login both rewrite its listings, so they take turns.
    private val locks = LoginLocks()
    private val counts = JmapMailCounts(connections, zone, clock)

    override suspend fun messages(scope: MailScope): InboxSnapshot = messages(scope, MailQuery())

    override suspend fun messages(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = load(scope, query) { refresh(it) }

    override suspend fun more(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = load(scope, query) { more(it) }

    override fun beginCounting() = counts.begin()

    override suspend fun count(
        scope: MailScope,
        query: MailQuery,
    ): Long? = counts.count(selected(scope), scope, query)

    override suspend fun cached(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = snapshot(selected(scope), scope, query, clock.now(), unavailable = emptyList())

    private suspend fun selected(scope: MailScope): List<JmapLogin> =
        withContext(Dispatchers.IO) {
            try {
                logins.logins()
            } catch (error: LoginStoreException) {
                throw InboxStorageException(error)
            }
        }.filter { scope.reads(it.id) }

    /** Runs [sync] for each login [scope] reads, then reads the lists from the cache. */
    private suspend fun load(
        scope: MailScope,
        query: MailQuery,
        sync: suspend MailAccountLists.(ListingsOf) -> Unit,
    ): InboxSnapshot {
        val selected = selected(scope)
        val now = clock.now()
        val failures =
            coroutineScope {
                selected.map { login -> async { syncLogin(login, scope, query, now, sync) } }.awaitAll()
            }
        return snapshot(selected, scope, query, now, failures.filterNotNull())
    }

    /** Why [login] could not be brought up to date, or null when it was or the query reads none of it. */
    private suspend fun syncLogin(
        login: JmapLogin,
        scope: MailScope,
        query: MailQuery,
        now: Instant,
        sync: suspend MailAccountLists.(ListingsOf) -> Unit,
    ): UnavailableAccount? {
        val conditions = emailConditions(query, login.id, now, zone()) ?: return null
        return try {
            cache.withAccount(login.id) { accountCache ->
                val connection = connections.connection(login)
                val accountId = connection.session().accountFor(JmapCapability.MAIL)
                if (accountId == null) {
                    UnavailableAccount(login.name, InboxFailure.NoMail)
                } else {
                    val lists =
                        MailAccountLists(connection, accountId, login.id, accountCache, limit) {
                            clock.now().toEpochMilliseconds()
                        }
                    locks.of(login.id).withLock {
                        lists.sync { mailboxes -> scope.listingQueries(mailboxes, login.id, query, conditions) }
                    }
                    null
                }
            }
        } catch (error: JmapException) {
            UnavailableAccount(login.name, error.toInboxFailure())
        }
    }

    /**
     * The cached rows of every selected login, merged so no list shows rows past where another
     * list with more stops. A thread can be in several chosen mailboxes; it is listed once, with
     * all of their emails.
     */
    private suspend fun snapshot(
        selected: List<JmapLogin>,
        scope: MailScope,
        query: MailQuery,
        now: Instant,
        unavailable: List<UnavailableAccount>,
    ): InboxSnapshot {
        val local = zone()
        val pages =
            selected.flatMap { login ->
                emailConditions(query, login.id, now, local)
                    ?.let { conditions ->
                        cache.withAccount(login.id) { it.cachedPages(login, scope, query, conditions, local) }
                    }.orEmpty()
            }
        val merged = pages.merged(query.filters.sort) { it.receivedAt }
        val messages =
            merged.rows
                .map { it.message }
                .groupBy { it.location.accountId }
                .values
                .flatMap { it.mergedByThread() }
                .filter(query.filters::keepsLoaded)
        return InboxSnapshot(messages, unavailable, merged.hasMore)
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
    }
}

private fun JmapException.toInboxFailure(): InboxFailure =
    when (this) {
        is JmapException.Unauthorized -> InboxFailure.SignInRejected
        is JmapException.Forbidden -> InboxFailure.NotPermitted
        is JmapException.CredentialsUnavailable -> InboxFailure.CredentialsUnavailable
        is JmapException.Unsupported -> InboxFailure.NoMail
        is JmapException.Unreachable -> InboxFailure.Unreachable
        else -> InboxFailure.ServerProblem
    }
