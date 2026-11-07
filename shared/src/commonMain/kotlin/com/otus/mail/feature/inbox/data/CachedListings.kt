package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapException
import com.otus.jmap.JmapFilter
import com.otus.jmap.jmapDecoding
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.ThreadObject
import com.otus.mail.core.mail.objects
import com.otus.mail.core.mail.receivedTime
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.data.ListingPage
import com.otus.mail.core.messages.data.ServerSites
import com.otus.mail.core.messages.data.ThreadView
import com.otus.mail.core.messages.data.boxes
import com.otus.mail.core.messages.data.inConversation
import com.otus.mail.core.messages.data.memberIds
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.data.toMessage
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailQuery
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** The list of each mailbox of [scope] among [mailboxes] that [query] keeps, with its [conditions]. */
internal fun MailScope.listingQueries(
    mailboxes: List<MailFolder>,
    loginId: String,
    query: MailQuery,
    conditions: List<JmapFilter>,
): List<ListingQuery> =
    chosen(mailboxes, loginId, query).map { mailbox ->
        ListingQuery(mailbox.id, mailFilter(listOf(mailbox.id), conditions), query.filters.sort)
    }

/**
 * [login]'s lists of [scope] among its kept [mailboxes] as the cache holds them, one page per
 * mailbox, mapped like loaded rows, trusting DMARC results only from [serverSite] (see
 * [ServerSites]). A mailbox without a saved list for [query] shows the rows of its unfiltered list
 * that the device can check against [query] at [now], or nothing when it cannot. None show when
 * the query reads none of the login's mail. A row whose email has left the mailbox since, such
 * as after a move, is left out. A row that no longer reads leaves the login out.
 */
internal suspend fun MailCache.cachedPages(
    login: JmapLogin,
    mailboxes: List<MailFolder>,
    scope: MailScope,
    query: MailQuery,
    now: Instant,
    zone: TimeZone,
    serverSite: String,
): List<ListingPage<ListedRow>> {
    val conditions = emailConditions(query, login.id, now, zone) ?: return emptyList()
    val check = localCheck(query, login.id, now, zone)
    val chosen = scope.chosen(mailboxes, login.id, query)
    val queries = scope.listingQueries(mailboxes, login.id, query, conditions)
    val unfiltered = chosen.map { ListingQuery(it.id, mailFilter(listOf(it.id), emptyList()), query.filters.sort) }
    val saved = listings(login.id, queries)
    // Each read waits its turn with a running sync, so only mailboxes without a list of their own
    // read the unfiltered one.
    val missing = unfiltered.filterIndexed { index, _ -> check != null && queries[index].key !in saved }
    val listings = saved + listings(login.id, missing)
    val held = heldMail(login.id, listings.values.flatMap { it.ids }.toSet())
    val view =
        ThreadView(
            shown = chosen.map { it.id }.toSet(),
            trashAndJunk = mailboxes.filter { it.isBin }.map { it.id }.toSet(),
            sent = mailboxes.filter { it.role == MailboxRole.Sent }.map { it.id }.toSet(),
            own = query.addresses[login.id].orEmpty() + login.username,
        )
    return try {
        jmapDecoding {
            chosen.indices.mapNotNull { index ->
                val mailbox = chosen[index]
                val list = ListedMailbox(login, mailbox, scope.nameOf(mailbox), view, zone, serverSite)
                val listing = listings[queries[index].key]
                when {
                    listing != null -> held.page(listing, list)
                    check != null -> listings[unfiltered[index].key]?.let { held.page(it, list, check) }
                    else -> null
                }
            }
        }
    } catch (expected: JmapException.NotJmap) {
        emptyList()
    }
}

/** A list row, with when its email arrived, which orders rows across lists. */
internal class ListedRow(
    val message: MailMessage,
    val receivedAt: Instant,
)

/** The mailbox a list shows, and what its rows are mapped with. */
private class ListedMailbox(
    val login: JmapLogin,
    val mailbox: MailFolder,
    val name: String,
    val view: ThreadView,
    val zone: TimeZone,
    val serverSite: String,
)

/** The cached row emails of some lists, their threads, and those threads' emails. */
private class HeldMail(
    val rows: Map<String, EmailObject>,
    val threads: Map<String, ThreadObject>,
    val members: Map<String, EmailObject>,
) {
    fun page(
        listing: Listing,
        list: ListedMailbox,
        check: ((ListedRow) -> Boolean)? = null,
    ): ListingPage<ListedRow> =
        ListingPage(
            listing.ids
                .mapNotNull { row(it, list) }
                .filter { check?.invoke(it) ?: true },
            listing.hasMore,
        )

    fun row(
        id: String,
        list: ListedMailbox,
    ): ListedRow? {
        val row = rows[id]?.takeIf { list.mailbox.id in it.boxes } ?: return null
        val thread = threads[row.threadId]?.emailIds.orEmpty().mapNotNull { members[it] }
        val login = list.login
        val message =
            row
                .toMessage(
                    MailLocation(
                        list.name,
                        login.name,
                        login.id,
                        login.username,
                        mailboxId = list.mailbox.id,
                        mailboxRole = list.mailbox.role,
                    ),
                    list.zone,
                    list.serverSite,
                ).inConversation(row, thread, list.mailbox.id, list.view, list.zone)
        return ListedRow(message, row.receivedTime)
    }
}

/** Thread emails come from the member objects, or from row objects for emails that are rows too. */
private suspend fun MailCache.heldMail(
    loginId: String,
    rowIds: Set<String>,
): HeldMail {
    val rows = objects(loginId, CacheKind.Email, EmailObject.serializer(), rowIds)
    val threadIds =
        rows.values
            .map { it.threadId }
            .filter { it.isNotEmpty() }
            .toSet()
    val threads = objects(loginId, CacheKind.Thread, ThreadObject.serializer(), threadIds)
    val memberIds = threads.values.flatMap { it.emailIds }.toSet()
    val members = objects(loginId, CacheKind.Member, EmailObject.serializer(), memberIds)
    val rowMembers = objects(loginId, CacheKind.Email, EmailObject.serializer(), memberIds - members.keys)
    return HeldMail(rows, threads, rowMembers + members)
}
