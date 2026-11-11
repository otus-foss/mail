package com.otusfoss.mail.testing

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.LoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.data.EmailChangeQueue
import com.otusfoss.mail.core.messages.domain.MailConversation
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.core.messages.domain.ThreadEmail
import com.otusfoss.mail.core.messages.domain.ThreadMember
import com.otusfoss.mail.feature.inbox.data.JmapMailSource
import kotlinx.datetime.TimeZone

/**
 * A mail source over [connections] as the app builds one: its changes wait in [queue], and its
 * folders are the ones [cache] keeps.
 */
internal fun mailSource(
    connections: JmapConnections,
    cache: MailCache = InMemoryMailCache(),
    limit: Int = PAGE,
    zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    queue: EmailChangeQueue = EmailChangeQueue(connections, cache, store = null),
): JmapMailSource = JmapMailSource(connections, cache, queue, Mailboxes(cache), limit, zone)

/** A mail source that reads this server, which offers [session], for the saved logins of [store], in UTC. */
internal fun FakeMailServer.source(
    store: LoginStore,
    cache: MailCache = InMemoryMailCache(),
    limit: Int = PAGE,
    queue: EmailChangeQueue? = null,
    session: String = STALWART_SESSION,
): JmapMailSource {
    val connections = connections(store, session)
    val changes = queue ?: EmailChangeQueue(connections, cache, store = null)
    return mailSource(connections, cache, limit, { TimeZone.UTC }, changes)
}

/** Connections to this server, which offers [session], for the saved logins of [store]. */
internal fun FakeMailServer.connections(
    store: LoginStore,
    session: String = STALWART_SESSION,
) = JmapConnections(JmapClient(stalwartLike(session, api = ::answer)), store)

private const val PAGE = 50

/** A conversation of [members], for actions that read only who belongs to it. */
internal fun conversationOf(members: List<ThreadMember>): MailConversation =
    MailConversation(
        threadId = "t1",
        messageCount = members.size,
        last = ThreadEmail(MailSender("Grace", "grace@example.org"), emptyList(), ReceivedAt("2026-10-04", "09:30")),
        senders = emptyList(),
        answered = false,
        members = members,
    )
