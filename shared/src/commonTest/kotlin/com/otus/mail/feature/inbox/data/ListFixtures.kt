package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.feature.inbox.domain.MailConversation
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.ThreadEmail
import com.otus.mail.feature.inbox.domain.ThreadMember
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.datetime.TimeZone

/** A mail source that reads this server, which offers [session], for the saved logins of [store], in UTC. */
internal fun FakeMailServer.source(
    store: LoginStore,
    cache: MailCache = InMemoryMailCache(),
    limit: Int = PAGE,
    queue: EmailChangeQueue? = null,
    session: String = STALWART_SESSION,
): JmapMailSource =
    JmapMailSource(connections(store, session), store, cache, limit, zone = {
        TimeZone.UTC
    }, queue = queue)

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
