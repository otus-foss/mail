package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.core.messages.data.EmailChangeQueue
import com.otus.mail.core.messages.data.JmapEmailWriter

/** The email writer the app builds, over [connections] and [cache], with changes kept in [queue]. */
internal fun emailWriter(
    connections: JmapConnections,
    cache: MailCache = InMemoryMailCache(),
    queue: EmailChangeQueue = EmailChangeQueue(connections, cache, store = null),
): JmapEmailWriter = JmapEmailWriter(connections, cache, queue, Mailboxes(cache))

/** Saving and sending over [connections], as the app builds them. */
internal fun composeMail(
    connections: JmapConnections,
    newMessageId: () -> String = ::draftMessageId,
): JmapComposeMail = JmapComposeMail(connections, Mailboxes(InMemoryMailCache()), newMessageId)
