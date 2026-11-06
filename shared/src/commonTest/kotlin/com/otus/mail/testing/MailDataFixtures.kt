package com.otus.mail.testing

import com.otus.jmap.JmapConnections
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.core.messages.data.EmailChangeQueue
import com.otus.mail.core.messages.data.JmapEmailWriter
import com.otus.mail.feature.compose.data.JmapComposeMail
import com.otus.mail.feature.compose.data.draftMessageId

/** The email writer the app builds, over [connections] and [cache], with changes kept in [queue]. */
internal fun emailWriter(
    connections: JmapConnections,
    cache: MailCache = InMemoryMailCache(),
    queue: EmailChangeQueue = EmailChangeQueue(connections, cache, store = null),
): JmapEmailWriter = JmapEmailWriter(connections, cache, queue, Mailboxes(cache))

/** Saving and sending over [connections], as the app builds them, with one cache for folders and marks. */
internal fun composeMail(
    connections: JmapConnections,
    newMessageId: () -> String = ::draftMessageId,
): JmapComposeMail {
    val cache = InMemoryMailCache()
    val changes = EmailChangeQueue(connections, cache, store = null)
    return JmapComposeMail(connections, Mailboxes(cache), changes, newMessageId)
}
