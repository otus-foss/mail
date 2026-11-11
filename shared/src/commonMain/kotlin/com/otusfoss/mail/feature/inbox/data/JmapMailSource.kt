package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.cache.CacheStorageException
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.cache.withAccount
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mail.toMailFailure
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.data.EmailChangeQueue
import com.otusfoss.mail.core.messages.data.ListingPage
import com.otusfoss.mail.core.messages.data.ServerSites
import com.otusfoss.mail.core.messages.data.merged
import com.otusfoss.mail.core.messages.data.mergedByThread
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.feature.inbox.domain.InboxSnapshot
import com.otusfoss.mail.feature.inbox.domain.InboxStorage
import com.otusfoss.mail.feature.inbox.domain.InboxStorageException
import com.otusfoss.mail.feature.inbox.domain.MailQuery
import com.otusfoss.mail.feature.inbox.domain.MailSource
import com.otusfoss.mail.feature.inbox.domain.UnavailableAccount
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Loads all or named mailboxes of each login, or chosen folders, through [cache]: a load brings the
 * cache up to date with the server and then reads the lists from it, so [cached] shows the same
 * rows without the server. Logins load in parallel and fail alone; a failed login still shows what
 * the cache holds. Each list loads [limit] conversations at a time. [sites] holds each login's
 * server domain, which a sender's DMARC result must come from to show a brand logo; a load finds
 * it, and the cached list reads only what is already known. Each load of a login runs inside
 * [queue]'s sync of it (see [EmailChangeQueue.syncing]), so waiting marks and moves go first and
 * still show after it. The lists read the folders [mailboxes] keeps.
 */
class JmapMailSource(
    private val connections: JmapConnections,
    private val cache: MailCache,
    private val queue: EmailChangeQueue,
    private val mailboxes: Mailboxes,
    private val limit: Int = DEFAULT_LIMIT,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val sites: ServerSites = ServerSites { null },
) : MailSource {
    private val clock: Clock = Clock.System
    private val counts = JmapMailCounts(connections, mailboxes, zone, clock)

    override suspend fun messages(scope: MailScope): InboxSnapshot = messages(scope, MailQuery())

    override suspend fun messages(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = load(scope, query) { refresh(it) }

    override suspend fun more(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = load(scope, query) { more(it) }

    override suspend fun count(
        scope: MailScope,
        query: MailQuery,
    ): Long? =
        if (query.filters.clientOnly) {
            // The server cannot tell these, so count the mail on the device. With none of it there, the
            // device may just not have loaded it, so the count stays unknown rather than 0.
            cached(scope, query)
                .messages
                .size
                .takeIf { it > 0 }
                ?.toLong()
        } else {
            counts.count(selected(scope), scope, query)
        }

    override suspend fun cached(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot =
        cacheErrors {
            snapshot(selected(scope), scope, query, clock.now(), unavailable = emptyList()) { sites.known(it.id) }
        }

    private suspend fun selected(scope: MailScope): List<JmapLogin> =
        try {
            connections.savedLogins()
        } catch (error: LoginStoreException) {
            throw InboxStorageException(error)
        }.filter { scope.reads(it.id) }

    /** Runs [sync] for each login [scope] reads, then reads the lists from the cache. */
    private suspend fun load(
        scope: MailScope,
        query: MailQuery,
        sync: suspend MailAccountLists.(ListingsOf) -> Unit,
    ): InboxSnapshot =
        cacheErrors {
            val selected = selected(scope)
            val now = clock.now()
            val failures =
                coroutineScope {
                    selected.map { login -> async { syncLogin(login, scope, query, now, sync) } }.awaitAll()
                }
            snapshot(selected, scope, query, now, failures.filterNotNull(), sites::find)
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
                val lists =
                    MailAccountLists(connections.mailAccount(login), mailboxes, accountCache, limit) {
                        clock.now().toEpochMilliseconds()
                    }
                queue.syncing(login.id) {
                    lists.sync { folders -> scope.listingQueries(folders, login.id, query, conditions) }
                }
                null
            }
        } catch (error: JmapException) {
            UnavailableAccount(login.name, error.toMailFailure(), cache.syncedAt(login.id))
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
        siteOf: suspend (JmapLogin) -> String,
    ): InboxSnapshot {
        val local = zone()
        val pages =
            coroutineScope {
                selected
                    .map { login -> async { cachedPages(login, scope, query, now, local, siteOf) } }
                    .awaitAll()
                    .flatten()
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

    /** [login]'s cached pages for [query]; none when the query reads none of its mail. */
    private suspend fun cachedPages(
        login: JmapLogin,
        scope: MailScope,
        query: MailQuery,
        now: Instant,
        zone: TimeZone,
        siteOf: suspend (JmapLogin) -> String,
    ): List<ListingPage<ListedRow>> {
        val folders = mailboxes.cached(login.id)
        val site = siteOf(login)
        return cache.withAccount(login.id) { kept -> kept.cachedPages(login, folders, scope, query, now, zone, site) }
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
    }
}

internal suspend fun <T> cacheErrors(operation: suspend () -> T): T =
    try {
        operation()
    } catch (error: CacheStorageException) {
        throw InboxStorageException(error, InboxStorage.Cache)
    }
